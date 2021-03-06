package org.zstack.header.volume;

import org.zstack.header.message.APIEvent;
/**
 *@apiResult
 * api event for message :ref:`APIChangeVolumeStateMsg`
 *
 *@since 0.1.0
 *
 *@example
 *
 * {
"org.zstack.header.volume.APICreateDataVolumeEvent": {
"inventory": {
"uuid": "f035366497994ef6bda20a45c4b3ee2e",
"name": "TestData",
"type": "Data",
"size": 10737418240,
"state": "Enabled",
"status": "NotInstantiated",
"createDate": "May 2, 2014 8:07:29 PM",
"lastOpDate": "May 2, 2014 8:07:29 PM",
"backupStorageRefs": []
},
"success": true
}
}
 */
public class APICreateDataVolumeEvent extends APIEvent {
	public APICreateDataVolumeEvent(String apiId) {
	    super(apiId);
    }
	
	public APICreateDataVolumeEvent() {
		super(null);
	}

    /**
     * @desc
     * see :ref:`VolumeInventory`
     */
	private VolumeInventory inventory;

	public VolumeInventory getInventory() {
    	return inventory;
    }

	public void setInventory(VolumeInventory inventory) {
    	this.inventory = inventory;
    }
}
