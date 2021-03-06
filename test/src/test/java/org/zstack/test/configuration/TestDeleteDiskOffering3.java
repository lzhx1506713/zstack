package org.zstack.test.configuration;

import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.componentloader.ComponentLoader;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.header.configuration.DiskOfferingInventory;
import org.zstack.header.volume.VolumeInventory;
import org.zstack.header.volume.VolumeVO;
import org.zstack.storage.volume.VolumeGlobalConfig;
import org.zstack.test.Api;
import org.zstack.test.ApiSenderException;
import org.zstack.test.BeanConstructor;
import org.zstack.test.DBUtil;
import org.zstack.utils.Utils;
import org.zstack.utils.data.SizeUnit;
import org.zstack.utils.logging.CLogger;

/**
 * 1. set volume.diskOffering.setNullWhenDeleting to false
 * 2. create a disk offering
 * 3. create a data volume from the disk offering
 * 4. delete the disk offering
 *
 * confirm the volume's disk offering column is not null
 */
public class TestDeleteDiskOffering3 {
    CLogger logger = Utils.getLogger(TestDeleteDiskOffering3.class);
    Api api;
    ComponentLoader loader;
    DatabaseFacade dbf;
    CloudBus bus;

    @Before
    public void setUp() throws Exception {
        DBUtil.reDeployDB();
        BeanConstructor con = new BeanConstructor();
        /* This loads spring application context */
        loader = con.addXml("PortalForUnitTest.xml").addXml("Simulator.xml").addXml("ZoneManager.xml")
                .addXml("PrimaryStorageManager.xml").addXml("ConfigurationManager.xml").addXml("VolumeManager.xml").addXml("AccountManager.xml").build();
        dbf = loader.getComponent(DatabaseFacade.class);
        bus = loader.getComponent(CloudBus.class);
        api = new Api();
        api.startServer();
    }

    @After
    public void tearDown() throws Exception {
        api.stopServer();
    }

    @Test
    public void test() throws ApiSenderException {
        VolumeGlobalConfig.UPDATE_DISK_OFFERING_TO_NULL_WHEN_DELETING.updateValue(false);
        DiskOfferingInventory dinv = new DiskOfferingInventory();
        dinv.setDiskSize(SizeUnit.GIGABYTE.toByte(10));
        dinv.setName("Test");
        dinv.setDescription("Test");
        dinv = api.addDiskOffering(dinv);

        VolumeInventory vinv = api.createDataVolume("TestData", dinv.getUuid());
        Assert.assertNotNull(vinv.getDiskOfferingUuid());
        api.deleteDiskOffering(dinv.getUuid());
        VolumeVO vol = dbf.findByUuid(vinv.getUuid(), VolumeVO.class);
        Assert.assertNotNull(vol.getDiskOfferingUuid());
    }
}
