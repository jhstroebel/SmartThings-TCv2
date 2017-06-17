/**
 *	TotalConnect Alarm API
 *
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
	//preferences go here
}
metadata {
	definition (name: "TotalConnect Alarm", namespace: "jhstroebel", author: "Brian Wilson") {
	capability "Lock"
	capability "Refresh"
	capability "Switch"
	attribute "status", "string"
}

simulator {
	// TODO: define status and reply messages here
}

tiles {
		standardTile("toggle", "device.status", width: 2, height: 2) {
			state("unknown", label:'${name}', action:"device.refresh", icon:"st.Office.office9", backgroundColor:"#ffa81e")
			state("Arming", label:'${name}', icon:"st.Home.home4", backgroundColor:"#ffa81e")
			state("Armed Stay", label:'${name}', action:"switch.off", icon:"st.Home.home4", backgroundColor:"#79b821", nextState:"Disarming")
			state("Armed Stay - Instant", label:'${name}', action:"switch.off", icon:"st.Home.home4", backgroundColor:"#79b821", nextState:"Disarming")
			state("Armed Night Stay", label:'${name}', action:"switch.off", icon:"st.Home.home4", backgroundColor:"#79b821", nextState:"Disarming")
			state("Armed Away", label:'${name}', action:"switch.off", icon:"st.Home.home3", backgroundColor:"#79b821", nextState:"Disarming")
			state("Armed Away - Instant", label:'${name}', action:"switch.off", icon:"st.Home.home4", backgroundColor:"#79b821", nextState:"Disarming")
			state("Disarming", label:'${name}', icon:"st.Home.home2", backgroundColor:"#ffa81e")
			state("Disarmed", label:'${name}', action:"lock.lock", icon:"st.Home.home2", backgroundColor:"#a8a8a8", nextState:"Arming")
		}
		standardTile("statusstay", "device.status", inactiveLabel: false, decoration: "flat") {
			state "default", label:'Arm Stay', action:"switch.on", icon:"st.Home.home4"
		}
		standardTile("statusaway", "device.status", inactiveLabel: false, decoration: "flat") {
			state "default", label:'Arm Away', action:"lock.lock", icon:"st.Home.home3"
		}
		standardTile("statusdisarm", "device.status", inactiveLabel: false, decoration: "flat") {
			state "default", label:'Disarm', action:"switch.off", icon:"st.Home.home2"
		}
		standardTile("refresh", "device.status", inactiveLabel: false, decoration: "flat") {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}

		main "toggle"
		details(["toggle", "statusaway", "statusstay", "statusdisarm", "refresh"])
	}
}

// Arm Function. Performs arming function
def armAway() {		   
	parent.armAway(this)
/*
	def metaData = panelMetaData(token, locationId) // Get AlarmCode
	if (metaData.alarmCode == 10201) {
		log.debug "Status is: Already Armed Away"
		sendEvent(name: "status", value: "Armed Away", displayed: "true", description: "Refresh: Alarm is Armed Away") 
	} else if (metaData.alarmCode == 10203) {
		log.debug "Status is: Armed Stay - Please Disarm First"
		sendEvent(name: "status", value: "Armed Stay", displayed: "true", description: "Refresh: Alarm is Armed Stay") 
    } else {
		log.debug "Status is: Arming"
        httpPost(paramsArm) // Arming Function in away mode
    }
*/
}

def armStay() {		   
	parent.armStay(this)
/*
	def metaData = panelMetaData(token, locationId) // Gets AlarmCode
	if (metaData.alarmCode == 10203) {
		log.debug "Status is: Already Armed Stay"
		sendEvent(name: "status", value: "Armed Stay", displayed: "true", description: "Refresh: Alarm is Armed Stay") 
	} else if (metaData.alarmCode == 10201) {
		log.debug "Status is: Armed Away - Please Disarm First"
		sendEvent(name: "status", value: "Armed Away", displayed: "true", description: "Refresh: Alarm is Armed Away") 
  	} else {
		log.debug "Status is: Arming"
        httpPost(paramsArm) // Arming function in stay mode
    }
*/
}

def disarm() {
	parent.disarm(this)
/*    
	def metaData = panelMetaData(token, locationId) // Gets AlarmCode
	if (metaData.alarmCode == 10200) {
		log.debug "Status is: Already Disarmed"
		sendEvent(name: "status", value: "Disarmed", displayed: "true", description: "Refresh: Alarm is Disarmed") 
	} else {
		log.debug "Status is: Disarming"
		httpPost(paramsDisarm)	
	} 
*/
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
        
    	if(device.currentState(name).value == value) {
        	isChange = false
        } else {
        	isChange = true
        }//if event isn't a change to that attribute
        
        isDisplayed = isChange
        
    	sendEvent(name: name, value: value, displayed: isDisplayed, isStateChange: isChange)
	}//goes through events if there are multiple
}//generateEvent

// handle commands
def lock() {
	log.debug "Executing 'Arm Away'"
	armAway()
	sendEvent(name: "lock", value: "lock", displayed: "true", description: "Arming Away") 
	sendEvent(name: "status", value: "Arming", displayed: "true", description: "Updating Status: Arming System")
	runIn(15,refresh)
}

def unlock() {
	log.debug "Executing 'Disarm'"
	disarm()
	sendEvent(name: "unlock", value: "unlock", displayed: "true", description: "Disarming") 
	sendEvent(name: "status", value: "Disarming", displayed: "true", description: "Updating Status: Disarming System") 
	runIn(15,refresh)
}

def on() {
	log.debug "Executing 'Arm Stay'"
	armStay()
	sendEvent(name: "switch", value: "on", displayed: "true", description: "Arming Stay") 
	sendEvent(name: "status", value: "Arming", displayed: "true", description: "Updating Status: Arming System") 
	runIn(15,refresh)
}

def off() {
	log.debug "Executing 'Disarm'"
	disarm()
	sendEvent(name: "switch", value: "off", displayed: "true", description: "Disarming") 
	sendEvent(name: "status", value: "Disarmed", displayed: "true", description: "Updating Status: Disarming System") 
	runIn(15,refresh)
}