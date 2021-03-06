package org.zstack.test.mevoco.billing;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.zstack.billing.*;
import org.zstack.cassandra.CassandraFacade;
import org.zstack.cassandra.CassandraOperator;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.componentloader.ComponentLoader;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.SimpleQuery.Op;
import org.zstack.core.db.UpdateQuery;
import org.zstack.header.allocator.HostCapacityOverProvisioningManager;
import org.zstack.header.identity.AccountConstant;
import org.zstack.header.identity.SessionInventory;
import org.zstack.header.storage.primary.PrimaryStorageOverProvisioningManager;
import org.zstack.header.vm.VmInstanceInventory;
import org.zstack.header.vm.VmInstanceState;
import org.zstack.network.service.flat.FlatNetworkServiceSimulatorConfig;
import org.zstack.simulator.kvm.KVMSimulatorConfig;
import org.zstack.storage.primary.local.LocalStorageSimulatorConfig;
import org.zstack.storage.primary.local.LocalStorageSimulatorConfig.Capacity;
import org.zstack.test.Api;
import org.zstack.test.ApiSenderException;
import org.zstack.test.DBUtil;
import org.zstack.test.WebBeanConstructor;
import org.zstack.test.deployer.Deployer;
import org.zstack.utils.CollectionUtils;
import org.zstack.utils.Utils;
import org.zstack.utils.data.SizeUnit;
import org.zstack.utils.gson.JSONObjectUtil;
import org.zstack.utils.logging.CLogger;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * usage state: S -> R -> R -> R -> S
 */
public class TestBilling1 {
    CLogger logger = Utils.getLogger(TestBilling1.class);
    Deployer deployer;
    Api api;
    ComponentLoader loader;
    CloudBus bus;
    DatabaseFacade dbf;
    SessionInventory session;
    LocalStorageSimulatorConfig config;
    FlatNetworkServiceSimulatorConfig fconfig;
    KVMSimulatorConfig kconfig;
    PrimaryStorageOverProvisioningManager psRatioMgr;
    HostCapacityOverProvisioningManager hostRatioMgr;
    long totalSize = SizeUnit.GIGABYTE.toByte(100);
    CassandraFacade cassf;
    CassandraOperator ops;

    @Before
    public void setUp() throws Exception {
        DBUtil.reDeployDB();
        DBUtil.reDeployCassandra(BillingConstants.CASSANDRA_KEYSPACE);
        WebBeanConstructor con = new WebBeanConstructor();
        deployer = new Deployer("deployerXml/mevoco/TestMevoco.xml", con);
        deployer.addSpringConfig("mevocoRelated.xml");
        deployer.addSpringConfig("cassandra.xml");
        deployer.addSpringConfig("billing.xml");
        deployer.load();

        loader = deployer.getComponentLoader();
        bus = loader.getComponent(CloudBus.class);
        dbf = loader.getComponent(DatabaseFacade.class);
        config = loader.getComponent(LocalStorageSimulatorConfig.class);
        fconfig = loader.getComponent(FlatNetworkServiceSimulatorConfig.class);
        kconfig = loader.getComponent(KVMSimulatorConfig.class);
        psRatioMgr = loader.getComponent(PrimaryStorageOverProvisioningManager.class);
        hostRatioMgr = loader.getComponent(HostCapacityOverProvisioningManager.class);
        cassf = loader.getComponent(CassandraFacade.class);
        ops = cassf.getOperator(BillingConstants.CASSANDRA_KEYSPACE);

        Capacity c = new Capacity();
        c.total = totalSize;
        c.avail = totalSize;

        config.capacityMap.put("host1", c);

        deployer.build();
        api = deployer.getApi();
        session = api.loginAsAdmin();
    }
    
	@Test
	public void test() throws ApiSenderException, InterruptedException {
        final VmInstanceInventory vm = deployer.vms.get("TestVm");
        api.stopVmInstance(vm.getUuid());

        double cprice = 100.01d;
        double mprice = 10.03d;

        APICreateResourcePriceMsg msg = new APICreateResourcePriceMsg();
        msg.setTimeUnit("s");
        msg.setPrice(cprice);
        msg.setResourceName(BillingConstants.SPENDING_CPU);
        api.createPrice(msg);

        msg = new APICreateResourcePriceMsg();
        msg.setTimeUnit("s");
        msg.setPrice(mprice);
        msg.setResourceName(BillingConstants.SPENDING_MEMORY);
        msg.setResourceUnit("m");
        api.createPrice(msg);
        
        UpdateQuery uq = UpdateQuery.New();
        uq.entity(VmUsageVO.class);
        uq.condAnd(VmUsageVO_.accountUuid, Op.EQ, AccountConstant.INITIAL_SYSTEM_ADMIN_UUID);
        uq.delete();

        class CreatePrice {
            void create(VmInstanceState state, Date date) {
                VmUsageVO u = new VmUsageVO();
                u.setAccountUuid(AccountConstant.INITIAL_SYSTEM_ADMIN_UUID);
                u.setVmUuid(vm.getUuid());
                u.setCpuNum(vm.getCpuNum());
                u.setMemorySize(vm.getMemorySize());
                u.setInventory(JSONObjectUtil.toJsonString(vm));
                u.setDateInLong(date.getTime());
                u.setName(vm.getName());
                u.setState(state.toString());
                dbf.persist(u);
            }
        }

        Date baseDate = new Date();
        CreatePrice cp = new CreatePrice();
        Date date1 = new Date(baseDate.getTime() + TimeUnit.DAYS.toMillis(1));
        cp.create(VmInstanceState.Stopped, date1);
        Date date2 = new Date(date1.getTime() + TimeUnit.DAYS.toMillis(2));
        cp.create(VmInstanceState.Running, date2);
        Date date3 = new Date(date2.getTime() + TimeUnit.DAYS.toMillis(6));
        cp.create(VmInstanceState.Running, date3);
        Date date4 = new Date(date3.getTime() + TimeUnit.DAYS.toMillis(2));
        cp.create(VmInstanceState.Running, date4);
        Date date5 = new Date(date4.getTime() + TimeUnit.DAYS.toMillis(10));
        cp.create(VmInstanceState.Stopped, date5);

        long during = date5.getTime() - date2.getTime();
        long duringInSeconds = TimeUnit.MILLISECONDS.toSeconds(during);

        logger.debug(String.format("expected seconds[%s]", duringInSeconds));

        final APICalculateAccountSpendingReply reply = api.calculateSpending(AccountConstant.INITIAL_SYSTEM_ADMIN_UUID, null, Long.MAX_VALUE, null);

        double cpuPrice = vm.getCpuNum() * cprice * duringInSeconds;
        double memPrice = SizeUnit.BYTE.toMegaByte(vm.getMemorySize()) * mprice * duringInSeconds;
        Assert.assertEquals(cpuPrice + memPrice, reply.getTotal(), 0.02);

        Spending spending = CollectionUtils.find(reply.getSpending(), arg -> BillingConstants.SPENDING_TYPE_VM.equals(arg.getSpendingType()) ? arg : null);
        Assert.assertNotNull(spending);

        VmSpending vmSpending = (VmSpending) spending.getDetails().get(0);
        double cpuSpending = (double) vmSpending.cpuInventory.stream().mapToDouble(i -> i.spending).sum();
        Assert.assertEquals(cpuPrice, cpuSpending, 0.02);

        double memSpending = (double) vmSpending.memoryInventory.stream().mapToDouble(i -> i.spending).sum();
        Assert.assertEquals(memPrice, memSpending, 0.02);
    }
}
