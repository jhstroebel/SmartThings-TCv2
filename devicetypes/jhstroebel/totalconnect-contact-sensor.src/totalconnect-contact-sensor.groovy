/**
 *  QCowboys's Version
 *  Version 1.1 - Added Bypass Zone capabilities (to clear bypass you need to disarm alarm system - maybe coming in future release)
 *  
 *  jhstroebel Branch off
 *  Version 1.2 - Initial version in new namespace for child devices of Service Manager
 *
 *  This is the Total Connect Open Close Sensor
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *  
 */

preferences {
	//Set preferences here?
}
metadata {
	definition (name: "TotalConnect Contact Sensor", namespace: "jhstroebel", author: "QCCowboy") {
	capability "Contact Sensor"
	capability "Sensor"
	capability "Refresh"
    capability "Switch"
	attribute "status", "string"
    attribute "contact", "string"
    
    command "updateSensor"
}

// UI tile definitions
	tiles {
		standardTile("contact", "device.contact", width: 2, height: 2) {
			state "open", label: "Faulted", icon: "st.contact.contact.open", backgroundColor: "#FF0000"
			state "closed", label: 'OK', icon: "st.contact.contact.closed", backgroundColor: "#79b821"
			state "bypassed", label: 'Bypassed', icon: "st.contact.contact.open", backgroundColor: "#2179b8"
			state "trouble", label: 'Troubled', icon: "st.contact.contact.open", backgroundColor: "#e5e500"
			state "tampered", label: 'Tampered', icon: "st.contact.contact.open", backgroundColor: "#FF0000"
			state "failed", label: 'Failed', icon: "st.contact.contact.open", backgroundColor: "#e5e500"
		}
		standardTile("refresh", "device.status", inactiveLabel: false, decoration: "flat") {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}
		standardTile("bypass", "device.status", inactiveLabel: false, decoration: "flat") {
			state "bypassed", label:'Bypass', action:"switch.off", icon:"st.Home.home30"
		}
		main "contact"
		details ("contact", "refresh", "bypass")
		}
}

// Zone status Information is below
// 0 – Normal
// 1 – Bypassed
// 2 – Faulted
// 8 – Trouble
// 16 – Tampered
// 32 – Supervision Failed

def refresh() {		   
	def token = login(token)
	String zname = device.name
	String locationId = settings.locationId
	String zoneID = settings.zoneID
    
    log.debug "Zname: ${zname}, zoneID: ${zoneID}"
    
    log.debug "Doing zone refresh"
	def metaData = zoneMetaData(token, locationId) // Gets Information
	
//    log.debug "Metadata " + metaData
    def currentStatus = metaData.get(zoneID)
    
    log.debug "Polled ZoneStatus is: " + currentStatus
    
    if (currentStatus == "0") {
    	log.debug "Zone ${zoneID} is OK"
        sendEvent(name: "contact", value:"closed", displayed: "true", description: "Refresh: Zone is closed", linkText: "Zone ${zoneID} - ${zname}", isStateChange: "true")
    } else if (currentStatus == "1") {
    	log.debug "Zone ${zoneID} is Bypassed"
        sendEvent(name: "contact", value:"bypassed", displayed: "true", description: "Refresh: Zone is bypassed", linkText: "Zone ${zoneID} - ${zname}", isStateChange: "true")
 	} else if (currentStatus == "2") {
    	log.debug "Zone ${zoneID} is Faulted"
        sendEvent(name: "contact", value:"open", displayed: "true", description: "Refresh: Zone is Faulted", linkText: "Zone ${zoneID} - ${zname}", isStateChange: "true")
     } else if (currentStatus == "8") {
    	log.debug "Zone ${zoneID} is Troubled"
        sendEvent(name: "contact", value:"trouble", displayed: "true", description: "Refresh: Zone is Troubled", linkText: "Zone ${zoneID} - ${zname}", isStateChange: "true")
     } else if (currentStatus == "16") {
    	log.debug "Zone ${zoneID} is Tampered"
        sendEvent(name: "contact", value:"tampered", displayed: "true", description: "Refresh: Zone is Tampered", linkText: "Zone ${zoneID} - ${zname}", isStateChange: "true")
     } else if (currentStatus == "32") {
    	log.debug "Zone ${zoneID} is Failed"
        sendEvent(name: "contact", value:"failed", displayed: "true", description: "Refresh: Zone is Failed", linkText: "Zone ${zoneID} - ${zname}", isStateChange: "true")
	 } 
     
	logout(token)
	sendEvent(name: "refresh", value: "true", displayed: "true", description: "Refresh Successful") 
}


def parse(String description) {
	log.debug "Parsing '${description}'"
}

// parse events into attributes
def parse(Map description) {
	log.debug "Parsing '${description}'"
    sendEvent(description)
}

def updateSensor(String status) {
	String zname = device.name
	String zoneID = settings.zoneID
    if (status == "0") {
    	log.debug "Zone ${zoneID} is OK"
        sendEvent(name: "contact", value:"closed", displayed: "true", description: "Refresh: Zone is closed", linkText: "Zone ${zoneID} - ${zname}", isStateChange: "true")
    } else if (status == "1") {
    	log.debug "Zone ${zoneID} is Bypassed"
        sendEvent(name: "contact", value:"bypassed", displayed: "true", description: "Refresh: Zone is bypassed", linkText: "Zone ${zoneID} - ${zname}", isStateChange: "true")
 	} else if (status == "2") {
    	log.debug "Zone ${zoneID} is Faulted"
        sendEvent(name: "contact", value:"open", displayed: "true", description: "Refresh: Zone is Faulted", linkText: "Zone ${zoneID} - ${zname}", isStateChange: "true")
     } else if (status == "8") {
    	log.debug "Zone ${zoneID} is Troubled"
        sendEvent(name: "contact", value:"trouble", displayed: "true", description: "Refresh: Zone is Troubled", linkText: "Zone ${zoneID} - ${zname}", isStateChange: "true")
     } else if (status == "16") {
    	log.debug "Zone ${zoneID} is Tampered"
        sendEvent(name: "contact", value:"tampered", displayed: "true", description: "Refresh: Zone is Tampered", linkText: "Zone ${zoneID} - ${zname}", isStateChange: "true")
     } else if (status == "32") {
    	log.debug "Zone ${zoneID} is Failed"
        sendEvent(name: "contact", value:"failed", displayed: "true", description: "Refresh: Zone is Failed", linkText: "Zone ${zoneID} - ${zname}", isStateChange: "true")
	 }
     else {
     	log.error "Zone ${zoneId} returned an unexpected value.  ZoneStatus: ${currentStatus}"
     }
     
	sendEvent(name: "refresh", value: "true", displayed: "true", description: "Refresh Successful") 
}

def off() {
	log.debug "Bypassing Sensor"
	def zname = device.name
	def bypassok
	def deviceID = settings.deviceId	
	def locationId = settings.locationId
	String zoneID = settings.zoneID
	def metaData = zoneMetaData(token, locationId) // Gets Informationbypass()
	log.debug "Bypassing Zone: ${zoneID}"
	def bypass = [
		uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/Bypass",
		body: [ SessionID: token, LocationID: locationId, DeviceID: deviceID, Zone: "${zoneID}", UserCode: '-1']
	]
//    log.debug bypass
	httpPost(bypass) {	response -> 
        bypassok = response.data
	}

	sendEvent(name: "switch", value: "off", displayed: "true", description: "Bypassing") 
	sendEvent(name: "contact", value: "bypassed", displayed: "true", description: "Status: Zone Bypassed")
    runIn(15,refresh)
}