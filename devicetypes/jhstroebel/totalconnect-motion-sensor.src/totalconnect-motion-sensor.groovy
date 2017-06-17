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
	// Preferences go here
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

def refresh() {		   
	parent.pollChildren(device)
    
	sendEvent(name: "refresh", value: "true", displayed: "true", description: "Refresh Successful") 
}


def parse(String description) {
	log.debug "Parsing '${description}'"
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
        
    	if(device.currentState(name).value == value) {
        	isChange = false
        } else {
        	isChange = true
        }//if event isn't a change to that attribute
        
        isDisplayed = isChange
        
    	sendEvent(name: name, value: value, displayed: isDisplayed, isStateChange: isChange)
	}//goes through events if there are multiple
}//generateEvent

def off() {
    log.debug "Bypassing Sensor"
	parent.bypassSensor(device)

	sendEvent(name: "switch", value: "off", displayed: "true", description: "Bypassing") 
	sendEvent(name: "contact", value: "bypassed", displayed: "true", description: "Status: Zone Bypassed")
    runIn(15,refresh)
}//bypass method