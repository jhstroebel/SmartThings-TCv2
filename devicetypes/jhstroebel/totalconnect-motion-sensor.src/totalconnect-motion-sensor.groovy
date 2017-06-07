/**
 *  QCowboys's Version
 *  Version 1.1 - Added Bypass Zone capabilities (to clear bypass you need to disarm alarm system - maybe coming in future release)
 *  
 *  jhstroebel Branch off
 *  Version 1.2 - Initial version in new namespace for child devices of Service Manager
 *
 *  This is the Total Connect Motion Sensor
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
	// See above ST thread above on how to configure the user/password.	 Make sure the usercode is configured
	// for whatever account you setup. That way, arming/disarming/etc can be done without passing a user code.
	input("userName", "text", title: "Username", description: "Your username for TotalConnect")
	input("password", "password", title: "Password", description: "Your Password for TotalConnect")
	// get this info by using https://github.com/mhatrey/TotalConnect/blob/master/TotalConnectTester.groovy 
	input("deviceId", "text", title: "Device ID - You'll have to look up", description: "Device ID")
	// get this info by using https://github.com/mhatrey/TotalConnect/blob/master/TotalConnectTester.groovy 
	input("locationId", "text", title: "Location ID - You'll have to look up", description: "Location ID")
	input("applicationId", "text", title: "Application ID - It is '14588' currently", description: "Application ID", defaultValue: "14588")
	input("applicationVersion", "text", title: "Application Version - use '3.0.32'", description: "Application Version", defaultValue: "3.0.32")
	input("zoneID", type: "number", title: "Zone ID - You'll have to look up", description: "Zone ID")
}
metadata {
	definition (name: "TotalConnect Motion Sensor", namespace: "jhstroebel", author: "QCCowboy") {
	capability "Motion Sensor"
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

// Login Function. Returns SessionID for rest of the functions
def login(token) {
	log.debug "Executed login"
	def paramsLogin = [
		uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/AuthenticateUserLogin",
		body: [userName: settings.userName , password: settings.password, ApplicationID: settings.applicationId, ApplicationVersion: settings.applicationVersion]
	]
	httpPost(paramsLogin) { responseLogin ->
		token = responseLogin.data.SessionID 
	}
	return token
} // Returns token		

// Logout Function. Called after every mutational command. Ensures the current user is always logged Out.
def logout(token) {
	//log.debug "During logout - ${token}"
	def paramsLogout = [
		uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/Logout",
		body: [SessionID: token]
	]
	httpPost(paramsLogout) { responseLogout ->
		log.debug "Smart Things has successfully logged out"
	}  
}

// Gets Zone Metadata. Takes token & location ID as an argument
Map zoneMetaData(token, locationId) {
	def tczones = [:]
	String zoneID
	String zoneStatus
	String zoneName
	def getPanelMetaDataAndFullStatusEx = [
		uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/GetPanelMetaDataAndFullStatusEx",
		body: [SessionID: token, LocationID: settings.locationId, LastSequenceNumber: 0, LastUpdatedTimestampTicks: 0, PartitionID: 1]
	]
	httpPost(getPanelMetaDataAndFullStatusEx) { responseSession -> 
        responseSession.data.PanelMetadataAndStatus.Zones.ZoneInfo.each
        {
			ZoneInfo ->
				zoneID = ZoneInfo.'@ZoneID'
				zoneStatus = ZoneInfo.'@ZoneStatus'
				zoneName = ZoneInfo.'@ZoneDescription'
//              log.debug "ZoneID: ${zoneID}, ZoneStatus: ${zoneStatus}, ZoneName: ${zoneName}"
				tczones.put(zoneID, zoneStatus)
        }
        
        log.debug tczones
    }
	return tczones
} //Should return zone information

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
	def token = login(token)
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
logout(token)
	sendEvent(name: "switch", value: "off", displayed: "true", description: "Bypassing") 
	sendEvent(name: "contact", value: "bypassed", displayed: "true", description: "Status: Zone Bypassed")
    runIn(15,refresh)
}