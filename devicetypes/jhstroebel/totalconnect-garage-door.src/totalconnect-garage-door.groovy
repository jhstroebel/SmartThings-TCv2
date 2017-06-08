/**
 *	TotalConnect Garage Door API
 *
 *	Code is slightly modified for a Garage Door Automation Device, but almost no code is original
 *	Copyright 2015 Brian Wilson 
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *	in compliance with the License. You may obtain a copy of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *	for the specific language governing permissions and limitations under the License.
 *
 *	This Device is based on work by @mhatrey (https://github.com/mhatrey/TotalConnect/blob/master/TotalConnect.groovy)
 *	The goal of this is to expose the TotalConnect Alarm to be used in other routines and in modes.	 To do this, I setup
 *	both lock and switch capabilities for it. Switch On = Armed Stay, Lock On = Armed Away, Switch/Lock Off = Disarm. 
 *	There are no tiles because I don't need them, but feel free to add them.  Also, you'll have to use @mhatrey's tester
 *	tool to get your deviceId and locationId.  See his thread for more info: 
 *	 https://community.smartthings.com/t/new-app-integration-with-honeywell-totalconnect-alarm-monitoring-system/
 *
 */

preferences {
	//Preferences Go Here
}
metadata {
	definition (name: "TotalConnect Garage Door", namespace: "jhstroebel", author: "Jeremy Stroebel") {
	capability "Refresh"
	capability "Switch"
    capability "Garage Door Control"
    attribute "status", "string"
}

simulator {
	// TODO: define status and reply messages here
}

tiles {
		standardTile("toggle", "device.status", width: 2, height: 2) {
			state("unknown", label:'${name}', action:"device.refresh", icon:"st.doors.garage.garage-open", backgroundColor:"#e86d13")
			state("Closed", label:'${name}', action:"switch.on", icon:"st.doors.garage.garage-closed", backgroundColor:"#00a0dc", nextState:"Open")
			state("Open", label:'${name}', action:"switch.off", icon:"st.doors.garage.garage-open", backgroundColor:"#e86d13", nextState:"Closed")
			state("Opening", label:'${name}', icon:"st.doors.garage.garage-opening", backgroundColor:"#e86d13")
			state("Closing", label:'${name}', icon:"st.doors.garage.garage-closing", backgroundColor:"#00a0dc")
		}
		standardTile("statusopen", "device.status", inactiveLabel: false, decoration: "flat") {
			state "default", label:'Open', action:"switch.on", icon:"st.doors.garage.garage-opening"
		}
		standardTile("statusclosed", "device.status", inactiveLabel: false, decoration: "flat") {
			state "default", label:'Close', action:"switch.off", icon:"st.doors.garage.garage-closing"
		}
		standardTile("refresh", "device.status", inactiveLabel: false, decoration: "flat") {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}

		main "toggle"
		details(["toggle", "statusclosed", "statusopen", "refresh"])
	}
}

def controlSwitch(int switchAction) {		   
	def token = login(token)
	def deviceId = settings.deviceId
	def switchId = settings.switchId
	def paramsControl = [
		uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/ControlASwitch",
		body: [SessionID: token, DeviceID: deviceId, SwitchID: switchId, SwitchAction: switchAction]
	]
	httpPost(paramsControl) // Sending Switch Control
    logout(token)
}

/* Think this was an attempt to update but used events instead...
def updateStatus(Integer switchState) {
	log.debug switchState

    if (switchState == 0) {
		log.debug "Status is: Closed"
		sendEvent(name: "status", value: "Closed", displayed: "true", description: "Refresh: Garage Door is Closed", isStateChange: "true") 
    } else if (switchState == 1) {
		log.debug "Status is: Open"
		sendEvent(name: "status", value: "Open", displayed: "true", description: "Refresh: Garage Door is Open", isStateChange: "true")
	} else {
    	log.error "Attempted to update switchState to ${switchState}. Only valid states are 0 or 1."
    }
}
*/

def refresh() {
	def token = login(token)
   	def deviceId = settings.deviceId
	def switchId = settings.switchId.toInteger()
	def switchState
    log.debug "Doing SwitchState refresh"
	def metaData = automationDeviceStatus(token, deviceId) // Gets Information
	//log.debug metaData
	if(metaData.containsKey(switchId)) {
		switchState = metaData.get(switchId)
	}
	else {
		log.debug "SwitchId ${switchId} does not exist"
	}

	log.debug switchState

    if (switchState == 0) {
		log.debug "Status is: Closed"
		sendEvent(name: "status", value: "Closed", displayed: "true", description: "Refresh: Garage Door is Closed", isStateChange: "true") 
    } else if (switchState == 1) {
		log.debug "Status is: Open"
		sendEvent(name: "status", value: "Open", displayed: "true", description: "Refresh: Garage Door is Open", isStateChange: "true")
	}

	logout(token)
	sendEvent(name: "refresh", value: "true", displayed: "true", description: "Refresh Successful") 
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
}

def parse(Map description) {
	log.debug "Parsing '${description}'"
    sendEvent(description)
}

// handle commands

def on() {
	log.debug "Executing 'Open'"
	controlSwitch(1)
	sendEvent(name: "switch", value: "on", displayed: "true", description: "Opening") 
	sendEvent(name: "status", value: "Open", displayed: "true", description: "Updating Status: Opening Garage Door") 
	runIn(15,refresh)
}

def off() {
	log.debug "Executing 'Closed'"
	controlSwitch(0)
	sendEvent(name: "switch", value: "off", displayed: "true", description: "Closing") 
	sendEvent(name: "status", value: "Close", displayed: "true", description: "Updating Status: Closing Garage Door") 
	runIn(15,refresh)
}