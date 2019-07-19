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
    capability "Door Control"
    capability "Garage Door Control"
	capability "Switch"
	capability "Momentary"
	capability "Refresh"
    
    attribute "status", "string"
}

simulator {
	// TODO: define status and reply messages here
}
tiles {
/*
		standardTile("toggle", "device.status", width: 3, height: 2) {
			state("unknown", label:'${name}', action:"device.refresh", icon:"st.doors.garage.garage-open", backgroundColor:"#e86d13")
			state("Closed", label:'${name}', action:"switch.on", icon:"st.doors.garage.garage-closed", backgroundColor:"#00a0dc", nextState:"Opening")
			state("Open", label:'${name}', action:"switch.off", icon:"st.doors.garage.garage-open", backgroundColor:"#e86d13", nextState:"Closing")
			state("Opening", label:'${name}', icon:"st.doors.garage.garage-opening", backgroundColor:"#e86d13")
			state("Closing", label:'${name}', icon:"st.doors.garage.garage-closing", backgroundColor:"#00a0dc")
		}
*/
		standardTile("toggle", "device.door", width: 3, height: 2) {
			state("unknown", label:'${name}', action:"device.refresh", icon:"st.doors.garage.garage-open", backgroundColor:"#e86d13")
			state("closed", label:'${name}', action:"switch.on", icon:"st.doors.garage.garage-closed", backgroundColor:"#00a0dc", nextState:"opening")
			state("open", label:'${name}', action:"switch.off", icon:"st.doors.garage.garage-open", backgroundColor:"#e86d13", nextState:"closing")
			state("opening", label:'${name}', icon:"st.doors.garage.garage-opening", backgroundColor:"#e86d13")
			state("closing", label:'${name}', icon:"st.doors.garage.garage-closing", backgroundColor:"#00a0dc")
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

def refresh() {
	parent.pollChildren(device)

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

// parse events into attributes
def generateEvent(List events) {
	//Default Values
    def isChange = false
	def isDisplayed = true
    
    events.each { it ->
    	log.debug it
        def name = it.get("name")
        def value = it.get("value")
        
    	if(device.currentState(name)?.value == value) {
        	isChange = false
        } else {
        	isChange = true
        }//if event isn't a change to that attribute
        
        isDisplayed = isChange
        
    	sendEvent(name: name, value: value, displayed: isDisplayed, isStateChange: isChange)
        if(name == "status") {
			if(device.currentState("door")?.value == value) {
        		isChange = false
       		} else {
				isChange = true
        	}//if event isn't a change to that attribute
    		isDisplayed = isChange
			
            sendEvent(name: "door", value: value, displayed: isDisplayed, isStateChange: isChange)
		}
	}//goes through events if there are multiple
}//generateEvent

// handle commands
def open() {
	on()
}

def close() {
	off()
}

def push() {    
    def latest = device.latestValue("door");
	log.debug "Garage door push button, current state $latest"

	switch (latest) {
    	case "open":
        	log.debug "Closing garage door"
        	close()
            sendEvent(name: "momentary", value: "pushed", isStateChange: true)
            break
            
        case "closed":
        	log.debug "Opening garage door"
        	open()
            sendEvent(name: "momentary", value: "pushed", isStateChange: true)
            break
            
        default:
        	log.debug "Can't change state of door, unknown state $latest"
            break
    }
}

def on() {
	log.debug "Executing 'Open'"
	parent.controlSwitch(device, 1)
	//sendEvent(name: "switch", value: "on", displayed: "true", description: "Opening") 
	sendEvent(name: "status", value: "opening", displayed: "true", description: "Updating Status: Opening Garage Door")
	sendEvent(name: "door", value: "opening", displayed: "true", description: "Updating Status: Opening Garage Door") 
	runIn(15,refresh)
}

def off() {
	log.debug "Executing 'Closed'"
	parent.controlSwitch(device, 0)
	//sendEvent(name: "switch", value: "off", displayed: "true", description: "Closing") 
	sendEvent(name: "status", value: "closing", displayed: "true", description: "Updating Status: Closing Garage Door") 
	sendEvent(name: "door", value: "closing", displayed: "true", description: "Updating Status: Closing Garage Door") 
	runIn(15,refresh)
}