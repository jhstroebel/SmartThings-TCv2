/**
 *	TotalConnect Lock API
 *
 *	Code is slightly modified for a Lock Automation Device, but almost no code is original
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
	definition (name: "TotalConnect Lock", namespace: "jhstroebel", author: "Jeremy Stroebel") {
	capability "Lock"
    capability "Actuator"
	capability "Switch"
	//capability "Momentary"
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
			state("Closed", label:'${name}', action:"switch.on", icon:"st.doors.garage.garage-closed", backgroundColor:"#00a0dc", nextState:"Unlocking")
			state("Open", label:'${name}', action:"switch.off", icon:"st.doors.garage.garage-open", backgroundColor:"#e86d13", nextState:"Locking")
			state("Opening", label:'${name}', icon:"st.doors.garage.garage-opening", backgroundColor:"#e86d13")
			state("Closing", label:'${name}', icon:"st.doors.garage.garage-closing", backgroundColor:"#00a0dc")
		}
*/
		standardTile("toggle", "device.lock", width: 3, height: 2) {
			state("unknown", label:'${name}', action:"device.refresh", icon:"st.locks.lock.unknown", backgroundColor:"#e86d13")
			state("locked", label:'${name}', action:"switch.on", icon:"st.locks.lock.locked", backgroundColor:"#00a0dc", nextState:"unlocking")
			state("unlocked", label:'${name}', action:"switch.off", icon:"st.locks.lock.unlocked", backgroundColor:"#e86d13", nextState:"locking")
			state("unlocking", label:'${name}', icon:"st.locks.lock.unlocked", backgroundColor:"#e86d13")
			state("locking", label:'${name}', icon:"st.locks.lock.locked", backgroundColor:"#00a0dc")
		}

		standardTile("statusunlocked", "device.status", inactiveLabel: false, decoration: "flat") {
			state "default", label:'Unlock', action:"switch.on", icon:"st.locks.lock.unlocked"
		}
		standardTile("statuslocked", "device.status", inactiveLabel: false, decoration: "flat") {
			state "default", label:'Lock', action:"switch.off", icon:"st.locks.lock.locked"
		}
		standardTile("refresh", "device.status", inactiveLabel: false, decoration: "flat") {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}

		main "toggle"
		details(["toggle", "statuslocked", "statusunlocked", "refresh"])
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
			if(device.currentState("lock")?.value == value) {
        		isChange = false
       		} else {
				isChange = true
        	}//if event isn't a change to that attribute
    		isDisplayed = isChange
			
            sendEvent(name: "lock", value: value, displayed: isDisplayed, isStateChange: isChange)
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

def unlock() {
	on()
}

def lock() {
	off()
}

/*
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
*/

//Should On be Lock and Off be Unlock???

def on() {
	log.debug "Executing 'Unlock'"
	parent.controlLock(device, 0)
	//sendEvent(name: "switch", value: "on", displayed: "true", description: "Unlocking") 
	sendEvent(name: "status", value: "unlocking", displayed: "true", description: "Updating Status: Unlocking ${name}")
	sendEvent(name: "door", value: "unlocking", displayed: "true", description: "Updating Status: Unlocking ${name}") 
	runIn(15,refresh)
}

def off() {
	log.debug "Executing 'Lock'"
	parent.controlLock(device, 1)
	//sendEvent(name: "switch", value: "off", displayed: "true", description: "Locking") 
	sendEvent(name: "status", value: "locking", displayed: "true", description: "Updating Status: Locking ${name}") 
	sendEvent(name: "door", value: "locking", displayed: "true", description: "Updating Status: Locking ${name}") 
	runIn(15,refresh)
}