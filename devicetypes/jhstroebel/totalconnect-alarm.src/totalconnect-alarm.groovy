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
    attribute "instant", "string"
    attribute "zonesBypassed", "string"
    
	command "setInstant"
	command "setDelay"
}

simulator {
	// TODO: define status and reply messages here
}

tiles (scale: 2) {
		multiAttributeTile(name: "toggle", type: "generic", width: 6, height: 4) {
        	tileAttribute("device.status", key: "PRIMARY_CONTROL"){
                attributeState "unknown", label:'${name}', action:"device.refresh", icon:"st.Office.office9", backgroundColor:"#ffa81e"
                attributeState "Arming", label:'${name}', icon:"st.Home.home4", backgroundColor:"#ffa81e"
                attributeState "Armed Stay", label:'${name}', action:"switch.off", icon:"st.Home.home4", backgroundColor:"#79b821", nextState:"Disarming"
                attributeState "Armed Stay - Instant", label:'${name}', action:"switch.off", icon:"st.Home.home4", backgroundColor:"#79b821", nextState:"Disarming"
                attributeState "Armed Night Stay", label:'${name}', action:"switch.off", icon:"st.Home.home4", backgroundColor:"#79b821", nextState:"Disarming"
                attributeState "Armed Away", label:'${name}', action:"switch.off", icon:"st.Home.home3", backgroundColor:"#79b821", nextState:"Disarming"
                attributeState "Armed Away - Instant", label:'${name}', action:"switch.off", icon:"st.Home.home4", backgroundColor:"#79b821", nextState:"Disarming"
                attributeState "Disarming", label:'${name}', icon:"st.Home.home2", backgroundColor:"#ffa81e"
                attributeState "Disarmed", label:'${name}', action:"lock.lock", icon:"st.Home.home2", backgroundColor:"#a8a8a8", nextState:"Arming"
			}//shows alarm status
            tileAttribute("device.zonesBypassed", key: "SECONDARY_CONTROL") {
            	attributeState "true", label:'Zones Bypassed'
                attributeState "false", label:''
            }//shows if zones are bypassed
		}
		standardTile("statusstay", "device.status", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label:'Arm Stay', action:"switch.on", icon:"st.Home.home4"
		}
		standardTile("statusaway", "device.status", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label:'Arm Away', action:"lock.lock", icon:"st.Home.home3"
		}
		standardTile("statusdisarm", "device.status", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label:'Disarm', action:"switch.off", icon:"st.Home.home2"
		}
		standardTile("instant", "device.instant", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "Delay", label:'Delay', action:"setInstant", icon:"st.security.alarm.clear"
			state "Instant", label:'Instant', action:"setDelay", icon:"st.security.alarm.alarm"
		}
		standardTile("refresh", "device.status", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}

		main "toggle"
		details(["toggle", "statusaway", "statusstay", "statusdisarm", "instant", "refresh"])
	}
}

def setInstant() {
    if(device.currentState("status")?.value == "Disarmed") {
    	sendEvent(name: "instant", value: "Instant", displayed: "true", description: "Alarm set to Instant")
        //only set instant while disarmed
	} else {
    	log.debug "Cannot set alarm to instant while its alarmed"
    }//else
}//setInstant

def setDelay() {
	if(device.currentState("status")?.value == "Disarmed") {
    	sendEvent(name: "instant", value: "Delay", displayed: "true", description: "Alarm set to Delay")
        //only set delay while disarmed
	} else {
    	log.debug "Cannot set alarm to delay while its alarmed"
    }//else
}//setInstant

// Arm Function. Performs arming function
def armAway() {		   
	if(device.currentState("instant")?.value == "Instant")
    {
    	parent.armAwayInstant(this)
    	sendEvent(name: "instant", value: "Delay", displayed: "false", description: "Alarm set to Delay") //reset Delay if on
	} else {
		parent.armAway(this)
	}//if instant is set, armAwayInstant, if not, armAway normally
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
}//armaway()

def armStay() {		   
	if(device.currentState("instant")?.value == "Instant")
    {
    	parent.armStayInstant(this)
    	sendEvent(name: "instant", value: "Delay", displayed: "false", description: "Alarm set to Delay") //reset Delay if on
	} else {
		parent.armStay(this)
	}//if instant is set, armAwayInstant, if not, armAway normally
}//armstay()

def disarm() {
	parent.disarm(this)
	if(device.currentState("instant")?.value == "Instant")
    {	
    	sendEvent(name: "instant", value: "Delay", displayed: "false", description: "Alarm set to Delay") //reset Delay if on
	}//if instant is set, reset to Delay (default)
}//disarm()

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
	runIn(5,refresh)
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
	sendEvent(name: "status", value: "Disarming", displayed: "true", description: "Updating Status: Disarming System") 
	runIn(5,refresh)
}