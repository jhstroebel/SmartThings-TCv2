/**
 *  TotalConnect Async
 *
 *  Version: v0.1
 *   Changes [June 6th, 2017]
 *  	- Started from code by mhatrey @ https://github.com/mhatrey/TotalConnect/blob/master/TotalConnect.groovy 	
 *  	- Modified locationlist to work (as List)
 *      - Added Autodiscover for Automation and Security Panel DeviceIDs
 *      - Added Iteration of devices to add (did all automation devices even though I only want a non-zwave garage door)
 *   	- Removed Success Page (unneccesary)
 *
 * Version: v1.0
 *	Changes [Jun 19th, 2017]
 *		- Too many to list.  Morphed into full service manager.
 *		- Code credits for TotalConnect pieces go to mhatrey, bdwilson, QCCowboy.  Without these guys, this would have never started
 *		- Reference credit to StrykerSKS (for Ecobee SM) and infofiend (for FLEXi Lighting) where ideas and code segments came from for the SM piece
 *      - Moved from open ended polling times (in seconds) to a list.  This allows us to use better scheduling with predictable options 1 minute and over.
 *  Changes [Jun 26, 2017] - v1.1
 *		- Updated deviceID detection code to use deviceClassId instead of Name since that works on more panels (Vista 20P tested)
 *		- Updated polling methods to fix Minute vs Minutes typo
 *
 * Version 2.0
 *  Changes [July 7, 2017]
 *		- Moved polling methods to async methods (increased timeout, etc)
 *  Changes [July 10, 2017] - v2.0.1
 *		- Changed PartitionID to 1 from 0 for enchanced capability
 *		- Added initialization of statusRefresh time variables to avoid errors (set to Long of 0)
 *  Changes [July 11, 2017] - v2.1
 *		- Went to generateEvents method in automationUpdater to deal with different types of devices in handlers using unified status
 *  Changes [July 13, 2017] - v2.2
 *		- Changes control methods to Async methods
 *		- Currently having issues with Synchronous HTTP methods (investigating)
 *		- Cleaned up code from v2.0 & v2.2 changes
 *  Changes [July 13, 2017] - v2.2.1
 *		- Added in Arming and Disarming statuses with an automatic refresh 3 seconds later
 *  Changes [July 25, 2017] - v2.3
 *		- Completely rewrite of settings to make async to work around issues with synchronous calls coming back as connection reset (still unknown why)
 *
 *  Future Changes Needed
 *      - Need to be able to run through settings and make changes (currently it won't actually get new data)
 *		- Add a settings to change credentials in preferences (currently can't get back into credentials page after initial setup unless credentials are failing login)
 *      - Implement Dimmers, Thermostats, & Locks
 *      - Any logic to run like harmony with hubs (automationDevice vs securityDevice) and subdevices?  seems unnecessarily complicated for this, but could provide a device that would give a dashboard view
 *		- Armed Away from Armed Stay or vice versa does not work.  Must disarm first (does not currently handle)
 *
 *  Copyright 2017 Jeremy Stroebel
 *
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

include 'asynchttp_v1'

definition(
    name: "TotalConnect 2.0 (Async)",
    namespace: "Security",
    author: "Jeremy Stroebel",
    description: "Total Connect 2.0 Service Manager",
    category: "My Apps", //Safety & Security"
    iconUrl: "https://s3.amazonaws.com/yogi/TotalConnect/150.png",
    iconX2Url: "https://s3.amazonaws.com/yogi/TotalConnect/300.png",
    singleInstance: true)

preferences {    	
	page(name: "credentials", content: "authPage")
	page(name: "locationsPage", content: "locationsPage")
    page(name: "deviceSetup", content: "deviceSetup")
	//only runs on first install
    page(name: "sensorSetup", content: "sensorSetup")
	//only runs when sensors are selected, should change to only ask if sensor list has changed?
}

/////////////////////////////////////
// PREFERENCE PAGES
/////////////////////////////////////

def authPage() {
	//Reset Settings
    //state.token = null
    //state.locationMap = null
    //state.sensors = null
    //state.switches = null
    //state.thermostats = null
    //state.locks = null
    
    state.setup = true
    
    if (!isTokenValid() && settings.userName != null && settings.password != null) {
		log.debug "Login Token Does not Exist or is likely invalid - Will attempt Login"
       	login()
    }//Check if there is no login token, but there are login details (this will check to make sure we're logged in before any refresh as well)
    
	//login page
    dynamicPage(name: "credentials", title:"TotalConnect 2.0 Login", nextPage: "locationsPage", uninstall: true, install:false) {
    	section ("TotalConnect 2.0 Login Credentials") {
       		paragraph "Give your Total Connect credentials. Recommended to make another user for SmartThings"
    		input("userName", "text", title: "Username", description: "Your username for TotalConnect")
    		input("password", "password", title: "Password", description: "Your Password for TotalConnect", submitOnChange:true)
		}//section
            			
        //Backend Values (at bottom)
    	section("Backend TotalConnect 2.0 Values - DO NOT CHANGE", hideable: true, hidden: true) {
			paragraph "These are required for login:"
           	input "applicationId", "text", title: "Application ID - It is '14588' currently", description: "Application ID", defaultValue: "14588"
			input "applicationVersion", "text", title: "Application Version - use '3.0.32'", description: "Application Version", defaultValue: "3.0.32"
		}//section
    }//dynamicPage()
}//authPage()

def locationsPage() {
//    state.setup = true
    if(!isTokenValid()) {
      	login()
    }//only login if we haven't
	
    dynamicPage(name:"locationsPage", title:"Locations", nextPage: "deviceSetup", uninstall: true, install:false, refreshInterval: 5) {
		if(!isTokenValid()) {
			section {
	           	paragraph "Please wait while we log you in.  This page will refresh in 5 seconds"
                //hopefully will never show, we login after we have details on prior page
            }//section
		} else if(isTokenValid() && !state.locationMap) {
			findLocations()
        
        	section {
	       		paragraph "You have been successfully logged in.  Please wait while we get Locations.  This page will refresh in 5 seconds"
        	}//section
		} else {
	    	//def deviceMap = getDeviceIDs(locations.get(selectedLocation))
            def options = state.locationMap.keySet() as List ?: []

            section("Select from the following Locations for Total Connect.") {
            	input "selectedLocation", "enum", required:true, title:"Select the Location", multiple:false, submitOnChange:true, options:options
            }//section
            if(settings.selectedLocation) {
            //Backend Values (at bottom)
       		section("Backend TotalConnect 2.0 Values - DO NOT CHANGE", hideable: true, hidden: true) {
				paragraph "These are required for device control:"
           		input "locationId", "text", title: "Location ID - Do not change", description: "Location ID", defaultValue: state.locationMap?.get(selectedLocation) ?: ""
				input "securityDeviceId", "text", title: "Security Device ID - Do not change", description: "Device ID", defaultValue: state.deviceMap?.get(state.locationMap?.get(selectedLocation)).get("1") //deviceMap?.get("Security Panel")
       			input "automationDeviceId", "text", required:false, title: "Automation Device ID - Do not change", description: "Device ID", defaultValue: state.deviceMap?.get(state.locationMap?.get(selectedLocation)).get("3") //deviceMap?.get("Automation")
            }//section
            }
        }//if we are logged in
	}//dynamicPage
}//locations page

private deviceSetup() {
	if(!state.sensors) {
      	findSensorDevices()
    } //this assumes all panels with an alarm have sensors (we also assume all panels have an alarm)
       
	if(settings.automationDeviceId && !(state.switches || state.thermostats || state.locks)) {
		findAutomationDevices()
    }//if we don't have automation devices, go get them
    
    def nextPage = null //default to no, assuming no sensors
    def install = true //default to true to allow install with no sensors
	if(zoneDevices) {
      	nextPage = "sensorSetup"
		install = false
	}//if we have sensors, make us go to sensorSetup
        
	return dynamicPage(name:"deviceSetup", title:"Pulling up the TotalConnect Device List!",nextPage: nextPage, install: install, uninstall: true, refreshInterval: 5) {
        if(zoneDevices) {
			nextPage = "sensorSetup"
			install = false
		} else {
			nextPage = null
			install = true
		} //only set nextPage if sensors are selected (and disable install)
		
        if(!state.sensors || (settings.automationDeviceId && !(state.switches || state.thermostats || state.locks))) {
            section {
	           	paragraph "Please wait while we get devices from TotalConnect.  This page will refresh in 5 seconds"
            }//section
        } else {
            def zoneMap
            def automationMap
            def thermostatMap
            def lockMap

            zoneMap = sensorsDiscovered()

            if(settings.automationDeviceId) {
                automationMap = switchesDiscovered()
                thermostatMap = thermostatsDiscovered()
                lockMap = locksDiscovered()
            }//only discover Automation Devices if there is an automation device with the given location

            def hideAlarmOptions = true
            if(alarmDevice) {
                hideAlarmOptions = false
            }//If alarm is selected, expand options

            def hidePollingOptions = true
            if(pollOn) {
                hidePollingOptions = false
            }//If alarm is selected, expand options

            Map pollingOptions = [5: "5 seconds", 10: "10 seconds", 15: "15 seconds", 20: "20 seconds", 30: "30 seconds", 60: "1 minute", 300: "5 minutes", 600: "10 minutes"]

            section("Select from the following Security devices to add in SmartThings.") {
                input "alarmDevice", "bool", required:true, title:"Honeywell Alarm", defaultValue:false, submitOnChange:true
                input "zoneDevices", "enum", required:false, title:"Select any Zone Sensors", multiple:true, options:zoneMap, submitOnChange:true
            }//section    
            section("Alarm Integration Options:", hideable: true, hidden: hideAlarmOptions) {
                input "shmIntegration", "bool", required: true, title:"Sync alarm status and SHM status", default:false
            }//section
            section("Select from the following Automation devices to add in SmartThings. (Suggest adding devices directly to SmartThings if compatible)") {
                input "automationDevices", "enum", required:false, title:"Select any Automation Devices", multiple:true, options:automationMap, hideWhenEmpty:true, submitOnChange:true
                input "thermostatDevices", "enum", required:false, title:"Select any Thermostat Devices", multiple:true, options:thermostatMap, hideWhenEmpty:true, submitOnChange:true
                input "lockDevices", "enum", required:false, title:"Select any Lock Devices", multiple:true, options:lockMap, hideWhenEmpty:true, submitOnChange:true
                //input "lockDevices", "enum", required:false, title:"Select any Lock Devices", multiple:true, options:lockDevices, hideWhenEmpty:true, submitOnChange:true
            }//section
            section("Enable Polling?") {
                input "pollOn", "bool", title: "Polling On?", description: "Pause or Resume Polling", submitOnChange:true
            }
            section("Polling Options (advise not to set any under 10 secs):", hideable: true, hidden: hidePollingOptions) {
                //input "panelPollingInterval", "number", required:pollOn, title: "Alarm Panel Polling Interval (in secs)", description: "How often the SmartApp will poll TC2.0"
                input "panelPollingInterval", "enum", required:(pollOn && settings.alarmDevice), title: "Alarm Panel Polling Interval", description: "How often the SmartApp will poll TC2.0", options:pollingOptions, default:60
                //input "zonePollingInterval", "number", required:pollOn, title: "Zone Sensor Polling Interval (in secs)", description: "How often the SmartApp will poll TC2.0"
                input "zonePollingInterval", "enum", required:(pollOn && settings.zoneDevices), title: "Zone Sensor Polling Interval", description: "How often the SmartApp will poll TC2.0", options:pollingOptions, default:60
                //input "automationPollingInterval", "number", required:pollOn, title: "Automation Polling Interval (in secs)", description: "How often the SmartApp will poll TC2.0"
                input "automationPollingInterval", "enum", required:(pollOn && (settings.automationDevices || settings.thermostatDevices || settings.lockDevices)), title: "Automation Polling Interval", description: "How often the SmartApp will poll TC2.0", options:pollingOptions, default:60
            }//section
    	}//we have the data needed to generate this page
	}//dynamicpage
}//deviceSetup

private sensorSetup() {
	dynamicPage(name:"sensorSetup", title:"Configure Sensor Types", install: true, uninstall: true) {
        def options = ["contactSensor", "motionSensor"] //sensor options
        
    	section("Select a sensor type for each sensor") {
        	settings.zoneDevices.each { dni ->
                input "${dni}_zoneType", "enum", required:true, title:"${state.sensors.find { ("TC-${settings.securityDeviceId}-${it.value.id}") == dni }?.value.name}", multiple:false, options:options
            }//iterate through selected sensors to get sensor type
		}//section
	}//dynamicPage
}//sensorSetup()

/////////////////////////////////////
// Setup/Device Discovery Functions
/////////////////////////////////////

def findLocations() {
    log.debug "Executed location discovery during setup"
	
    tcCommandAsync("GetSessionDetails", [SessionID: state.token, ApplicationID: settings.applicationId, ApplicationVersion: settings.applicationVersion])
}//findLocations()

def findSensorDevices() {
	log.debug "Executing sensor device discovery during setup"

    tcCommandAsync("GetPanelMetaDataAndFullStatusEx", [SessionID: state.token, LocationID: settings.locationId, LastSequenceNumber: 0, LastUpdatedTimestampTicks: 0, PartitionID: 1])
}//findSensorDevices()

def findAutomationDevices() {
	log.debug "Executing automation discovery during setup"

	tcCommandAsync("GetAllAutomationDeviceStatusEx", [SessionID: state.token, DeviceID: automationDeviceId, AdditionalInput: ''])
}//findAutomationDevices()

Map sensorsDiscovered() {
	def sensors =  state.sensors //needs some error checking likely
	def map = [:]

	sensors.each {
		def value = "${it?.value?.name}"
		def key = "TC-${settings.securityDeviceId}-${it?.value?.id}" //Sets DeviceID to "TC-${SecurityID}-${ZoneID}.  Follows format of Harmony activites
		map[key] = value
	}//iterate through discovered sensors to find value
    
    //log.debug "Sensors Options: " + map
	return map
}//returns list of sensors for preferences page

Map switchesDiscovered() {
	def switches =  state.switches //needs some error checking likely
	def map = [:]

	switches.each {
		def value = "${it?.value?.name}"
		def key = "TC-${settings.automationDeviceId}-${it?.value?.id}" //Sets DeviceID to "TC-${AutomationID}-${SwitchID}.  Follows format of Harmony activites
		map[key] = value
	}//iterate through discovered switches to find value
    
    //log.debug "Switches Options: " + map
	return map
}//returns list of switches for preferences page

Map thermostatsDiscovered() {
	def thermostats = state.thermostats
    def map = [:]
	
    thermostats.each {
		def value = "${it?.value?.name}"
		def key = "TC-${settings.automationDeviceId}-${it?.value?.id}" //Sets DeviceID to "TC-${AutomationID}-${ThermostatID}.  Follows format of Harmony activites
		map[key] = value
	}//iterate through discovered thermostats to find value
    
    //log.debug "Thermostat Options: " + map
	return map
}//thermostatsDiscovered()    

Map locksDiscovered() {
	def locks =  state.locks //needs some error checking likely
	def map = [:]

	locks.each {
			def value = "${it?.value?.name}"
			def key = "TC-${settings.automationDeviceId}-${it?.value?.id}" //Sets DeviceID to "TC-${AutomationID}-${LockID}.  Follows format of Harmony activites
			map[key] = value
	}//iterate through discovered locks to find value
    
    //log.debug "Locks Options: " + map
	return map
}//returns list of locks for preferences page

/////////////////////////////////////
// TC2.0 Authentication Methods
/////////////////////////////////////

// Login Function. Returns SessionID for rest of the functions (doesn't seem to test if login is incorrect...)
def login() {
    log.debug "Executing login"
	
    tcCommandAsync("AuthenticateUserLogin", [userName: settings.userName , password: settings.password, ApplicationID: settings.applicationId, ApplicationVersion: settings.applicationVersion])

    //return token
} // Used to return token        

// Keep Alive Command to keep session alive to reduce login/logout calls.  Keep alive does not confirm it worked so we will use GetSessionDetails instead.
// Currently there is no check to see if this is needed.  Logic for if keepAlive is needed would be state.token != null && now()-state.tokenRefresh < 240000.
// This works on tested assumption token is valid for 4 minutes (240000 milliseconds)
def keepAlive() {
	log.debug "KeepAlive.  State.token: '" + state.token + "'"

    tcCommandAsync("GetSessionDetails", [SessionID: state.token, ApplicationID: settings.applicationId, ApplicationVersion: settings.applicationVersion])
}//keepAlive

def isTokenValid() {
	//return false if token doesn't exist
    if(state.token == null) {
    	return false }
    
    Long timeSinceRefresh = now() - state.tokenRefresh
    
    //return false if time since refresh is over 4 minutes (likely timeout)       
    if(timeSinceRefresh > 240000) {
    	return false }
    
    return true
} // This is a logical check only, assuming known timeout values and clearing token on loggout.  This method does no testing of the actual token against TC2.0.

// Logout Function. Called after every mutational command. Ensures the current user is always logged Out.
def logout() {
	log.debug "During logout - ${state.token}"
  
	tcCommandAsync("Logout", [SessionID: state.token])
}

/////////////////////////////////////
// SmartThings defaults
/////////////////////////////////////

def initialize() {
    state.setup = false
    
    if(!isTokenValid()) {
    	login()
    }//if we don't have a valid login token (we should, from setup unless we took forever
    pause(3000)
    log.debug "Initialize" //Login produced token: " + state.token
    
// Combine all selected devices into 1 variable to make sure we have devices and to deleted unused ones
	state.selectedDevices = (settings.zoneDevices?:[]) + (settings.automationDevices?:[]) + (settings.thermostatDevices?:[]) + (settings.lockDevices?:[])
	
    if(settings.alarmDevice) {
		//state.selectedDevices.add("TC-${settings.securityDeviceId}") //this doesn't work, but should... its a typecasting thing
        def d = getChildDevice("TC-${settings.securityDeviceId}")
		log.debug "deviceNetworkId: ${d?.deviceNetworkId}"
        state.selectedDevices.add(d?.deviceNetworkId)
    }//if alarm device is selected
    
    //log.debug "Selected Devices: ${state.selectedDevices}"
    
    //delete devices that are not selected anymore (something is wrong here... it likes to delete the alarm device)
    def delete = getChildDevices().findAll { !state.selectedDevices.contains(it.deviceNetworkId) }
    log.debug "Devices to delete: ${delete}"
	removeChildDevices(delete)

	if (state.selectedDevices  || settings.alarmDevice) {
    	log.debug "Running addDevices()"
        addDevices()
    }//addDevices if we have any
	
    //initialize last refresh variables to avoid possible Null condition
    state.alarmStatusRefresh = 0L
	state.zoneStatusRefresh = 0L
	state.automationStatusRefresh = 0L
    
    //pollChildren()

	if (settings.alarmDevice && settings.shmIntegration) {
		log.debug "Setting up SHM + TC Alarm Integration"     
        //subscribe(location, "alarmSystemStatus", modeChangeHandler) //Check for changes to location mode (not the same as SHM)
        subscribe(location, "alarmSystemStatus", alarmHandler) //Check for changes to SHM and set alarm
    }//if alarm enabled & smh integration enabled
    else {
    	log.debug "SHM + TC Alarm Integration not enabled.  alarmDevice: ${settings.alarmDevice}, shmIntegration: ${shmIntegration}"
    }//if SHM + TC Alarm integration is off

    //Check for our schedulers and token every 2 minutes.  Well inside the tested 4.5+ min expiration
    schedule("0 0/2 * 1/1 * ? *", scheduleChecker)
	spawnDaemon()
}//initialize()

def installed() {
	log.debug "Installed with settings: ${settings}"
	state.firstRun = false //only run authentication on 1st setup.  After installed, it won't run again

	initialize()
}//installed()
    
def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
    unschedule()
	initialize()
}//updated()

def uninstalled() {
    removeChildDevices(getChildDevices())
}//uninstalled()

/////////////////////////////////////
// Scheduling and Updating
/////////////////////////////////////

def scheduleChecker() {    
	if(settings.pollOn) {
    	if(settings.alarmDevice) {
        	if(((now()-state.alarmStatusRefresh)/1000) > (settings.panelPollingInterval.toInteger()*1.5)) {
        		panelAutoUpdater()
                log.debug "Panel AutoUpdater Restarted"
            }//if we've potentially missed 2 updates
		}//if there is an alarm device
        if(settings.zoneDevices) {
        	if(((now()-state.zoneStatusRefresh)/1000) > (settings.zonePollingInterval.toInteger()*1.5)) {
            	zoneAutoUpdater()
                log.debug "Zone AutoUpdater Restarted"
            }//if we've potentially missed 2 updates
        }//if theres a zoneDevice
		if(settings.automationDevices) {
        	if(((now()-state.automationStatusRefresh)/1000) > (settings.automationPollingInterval.toInteger()*1.5)) {
				automationAutoUpdater()
                log.debug "Automation AutoUpdater Restarted"
            }//if we've potentially missed 2 updates
        }//if there is an automationDevice
	}//if polling is on
   
	if(((now()-state.tokenRefresh)/1000) > 149) {
    	keepAlive()
	}//if token will expire before we run again (every 2 minutes), by putting this at the end, we may have already refreshed the token
}//scheduleChecker()

def spawnDaemon() {
	if(settings.pollOn) {
		log.debug "Starting AutoUpdate schedules at ${new Date()}"
    	if(settings.alarmDevice) {
			switch(settings.panelPollingInterval.toInteger()) {
            	case 60:
                	runEvery1Minute(panelAutoUpdater)
                    break
                case 300:
                	runEvery5Minutes(panelAutoUpdater)
                    break
				case 600:
                	runEvery10Minutes(panelAutoUpdater)
                    break
                default:
                    runIn(settings.panelPollingInterval.toInteger(), panelAutoUpdater)
                    break
            }//switch
		}//if alarmDevice is selected
        if(settings.zoneDevices) {
        	switch(settings.zonePollingInterval.toInteger()) {
            	case 60:
                	runEvery1Minute(zoneAutoUpdater)
                    break
                case 300:
                	runEvery5Minutes(zoneAutoUpdater)
                    break
				case 600:
                	runEvery10Minutes(zoneAutoUpdater)
                    break
                default:
                    runIn(settings.zonePollingInterval.toInteger(), zoneAutoUpdater)
                    break
            }//switch
        }//if zoneDevices are selected
		if(settings.automationDevices) {
			switch(settings.automationPollingInterval.toInteger()) {
            	case 60:
                	runEvery1Minute(automationAutoUpdater)
                    break
                case 300:
                	runEvery5Minutes(automationAutoUpdater)
                    break
				case 600:
                	runEvery10Minutes(automationAutoUpdater)
                    break
                default:
                    runIn(settings.automationPollingInterval.toInteger(), automationAutoUpdater)
                    break
            }//switch
		}//if automationDevices are selected
    } else {
    	log.debug "Polling is turned off.  AutoUpdate canceled"
	}//if polling is on
}//spawnDaemon()

//AutoUpdates run if the time since last update (manual or scheduled) is 1/2 the setting (for example setting is 30 seconds, we'll poll after 15 have passed and schedule next one for 30 seconds)
def panelAutoUpdater() {
	if(((now()-state.alarmStatusRefresh)/1000) > (settings.panelPollingInterval.toInteger()/2)) {
    	log.debug "AutoUpdate Panel Status at ${new Date()}"
		//tcCommandAsync("GetPanelMetaDataAndFullStatusEx", "SessionID=${state.token}&LocationID=${settings.locationId}&LastSequenceNumber=0&LastUpdatedTimestampTicks=0&PartitionID=1") //This updates panel status
		tcCommandAsync("GetPanelMetaDataAndFullStatusEx", [SessionID: state.token, LocationID: settings.locationId, LastSequenceNumber: 0, LastUpdatedTimestampTicks: 0, PartitionID: 1]) //This updates panel status

        //state.alarmStatus = alarmPanelStatus()
        //updateStatuses()
    } else {
    	log.debug "Update has happened since last run, skipping this execution"
    }//if its not time to update
	if(settings.panelPollingInterval.toInteger() < 60) {
    	runIn(settings.panelPollingInterval.toInteger(), panelAutoUpdater)
    }//if our polling interval is less than 60 seconds, we need to manually schedule next occurance
}//updates panel status

def zoneAutoUpdater() {
	if(((now()-state.zoneStatusRefresh)/1000) > (settings.zonePollingInterval.toInteger()/2)) {
    	log.debug "AutoUpdate Zone Status at ${new Date()}"
        tcCommandAsync("GetZonesListInStateEx", [SessionID: state.token, LocationID: settings.locationId, PartitionID: 1, ListIdentifierID: 0])

		//state.zoneStatus = zoneStatus()
        //updateStatuses()
    } else {
    	log.debug "Update has happened since last run, skipping this execution"
    }//if its not time to update
	if(settings.zonePollingInterval.toInteger() < 60) {
		runIn(settings.zonePollingInterval.toInteger(), zoneAutoUpdater)
    }//if our polling interval is less than 60 seconds, we need to manually schedule next occurance
}//updates zone status(es)

def automationAutoUpdater() {
	if(((now()-state.automationStatusRefresh)/1000) > (settings.automationPollingInterval.toInteger()/2)) {
    	log.debug "AutoUpdate Automation Status at ${new Date()}"
        tcCommandAsync("GetAllAutomationDeviceStatusEx", [SessionID: state.token, DeviceID: settings.automationDeviceId, AdditionalInput: ''])
        
        //state.switchStatus = automationDeviceStatus()
		//updateStatuses()
	} else {
    	log.debug "Update has happened since last run, skipping this execution"
    }//if its not time to update
	if(settings.automationPollingInterval.toInteger() < 60) {
		runIn(settings.automationPollingInterval.toInteger(), automationAutoUpdater)
    }//if our polling interval is less than 60 seconds, we need to manually schedule next occurance
}//updates automation status(es)
            
/////////////////////////////////////
// HANDLERS
/////////////////////////////////////

/*
// Logic for Triggers based on mode change of SmartThings
def modeChangeHandler(evt) {	
	log.debug "Mode Change handler triggered.  Evt: ${evt.value}"
    
    //create alarmPanel object to use as shortcut
    state.alarmPanel = getChildDevice("TC-${settings.securityDeviceId}")
    
    if (evt.value == "Away") {
		log.debug "Mode is set to Away, Performing ArmAway"
		alarmPanel.armAway()
		//alarmPanel.lock()
	}//if mode changes to Away
	else if (evt.value == "Night") {
		log.debug "Mode is set to Night, Performing ArmStay"
		alarmPanel.armStay()
		//alarmPanel.on()
	}//if mode changes to Night
	else if (evt.value == "Home") {
		log.debug "Mode is set to Home, Performing Disarm"
		alarmPanel.disarm()
		//alarmPanel.off()
	}//if mode changes to Home
}//modeChangeHandler(evt)
*/

// Logic for Triggers based on mode change of SmartThings
def alarmHandler(evt) {	
    //create alarmPanel object to use as shortcut
    def alarmPanel = getChildDevice("TC-${settings.securityDeviceId}")

	//log.debug "SHM Change handler triggered.  Evt: ${evt.value}"
    //log.debug "Alarm Panel status is: ${alarmPanel.currentStatus}"
    
    if (evt.value == "away" && !(alarmPanel.currentStatus == "Armed Away" || alarmPanel.currentStatus == "Armed Away - Instant")) {
		log.debug "SHM Mode is set to Away, Performing ArmAway"
		alarmPanel.armAway()
		//alarmPanel.lock()
	}//if mode changes to Away and the Alarm isn't already in that state (since we fire events to SHM on updates)
	else if (evt.value == "stay"&& !(alarmPanel.currentStatus == "Armed Stay" || alarmPanel.currentStatus == "Armed Stay - Instant")) {
		log.debug "SHM Mode is set to Stay, Performing ArmStay"
		alarmPanel.armStay()
		//alarmPanel.on()
	}//if mode changes to Stay and the Alarm isn't already in that state (since we fire events to SHM on updates)
	else if (evt.value == "off" && alarmPanel.currentStatus != "Disarmed") {
		log.debug "SHM Mode is set to Off, Performing Disarm"
		alarmPanel.disarm()
		//alarmPanel.off()
	}//if mode changes to Off and the Alarm isn't already in that state (since we fire events to SHM on updates)
}//alarmHandler(evt)

/////////////////////////////////////
// CHILD DEVICE MANAGEMENT
/////////////////////////////////////

def addDevices() {
/* SmartThings Documentation adding devices, maybe add Try and Catch Block?
    settings.devices.each {deviceId ->
        try {
            def existingDevice = getChildDevice(deviceId)
            if(!existingDevice) {
                def childDevice = addChildDevice("smartthings", "Device Name", deviceId, null, [name: "Device.${deviceId}", label: device.name, completedSetup: true])
            }
        } catch (e) {
            log.error "Error creating device: ${e}"
        }
    }
*/
	if(settings.alarmDevice) {
		def deviceID = "TC-${settings.securityDeviceId}"
        def d = getChildDevice(deviceID)
        if(!d) {
			d = addChildDevice("jhstroebel", "TotalConnect Alarm", deviceID, null /*Hub ID*/, [name: "Device.${deviceID}", label: "TotalConnect Alarm", completedSetup: true])
		}//Create Alarm Device if doesn't exist
    }//if Alarm is selected

	if(settings.zoneDevices) {      
        //log.debug "zoneDevices: " + settings.zoneDevices
        def sensors = state.sensors

		settings.zoneDevices.each { dni ->
            def d = getChildDevice(dni)
            if(!d) {
            	def newSensor
                newSensor = sensors.find { ("TC-${settings.securityDeviceId}-${it.value.id}") == dni }
				log.debug "dni: ${dni}, newSensor: ${newSensor}"
                if(settings["${dni}_zoneType"] == "motionSensor") {
					d = addChildDevice("jhstroebel", "TotalConnect Motion Sensor", dni, null /*Hub ID*/, [name: "Device.${dni}", label: "${newSensor?.value.name}", completedSetup: true])
				}
                if(settings["${dni}_zoneType"] == "contactSensor") {
					d = addChildDevice("jhstroebel", "TotalConnect Contact Sensor", dni, null /*Hub ID*/, [name: "Device.${dni}", label: "${newSensor?.value.name}", completedSetup: true])
                }
			}//if it doesn't already exist
       	}//for each selected sensor
	}//if there are zoneDevices
    
    if(settings.automationDevices) {
        //log.debug "automationDevices: " + settings.automationDevices
        def switches = state.switches

		settings.automationDevices.each { dni ->
            def d = getChildDevice(dni)
            if(!d) {
            	def newSwitch
                newSwitch = switches.find { ("TC-${settings.automationDeviceId}-${it.value.id}") == dni }
                if("${newSwitch?.value.type}" == "1") {
					d = addChildDevice("jhstroebel", "TotalConnect Switch", dni, null /*Hub ID*/, [name: "Device.${dni}", label: "${newSwitch?.value.name}", completedSetup: true])
				}
                if("${newSwitch?.value.type}" == "2") {
					d = addChildDevice("jhstroebel", "TotalConnect Dimmer", dni, null /*Hub ID*/, [name: "Device.${dni}", label: "${newSwitch?.value.name}", completedSetup: true])
                }
				if("${newSwitch?.value.type}" == "3") {
					d = addChildDevice("jhstroebel", "TotalConnect Garage Door", dni, null /*Hub ID*/, [name: "Device.${dni}", label: "${newSwitch?.value.name}", completedSetup: true])
                }
			}//if it doesn't already exist
       	}//for each selected sensor
	}//if automation devices are selected

//No device handler exists yet... commented out addChildDeviceLine until that is resolved...
	if(settings.thermostatDevices) {      
        //log.debug "thermostatDevices: " + settings.thermostatDevices
        def thermostats = state.thermostats

		settings.thermostatDevices.each { dni ->
            def d = getChildDevice(dni)
            if(!d) {
            	def newThermostat
                newThermostat = thermostats.find { ("TC-${settings.automationDeviceId}-${it.value.id}") == dni }

//				d = addChildDevice("jhstroebel", "TotalConnect Thermostat", dni, null /*Hub ID*/, [name: "Device.${dni}", label: "${newThermostat?.value.name}", completedSetup: true])
			}//if it doesn't already exist
       	}//for each selected thermostat
	}//if there are thermostatDevices

//No device handler exists yet... commented out addChildDeviceLine until that is resolved...
	if(settings.lockDevices) {      
        //log.debug "lockDevices: " + settings.lockDevices
        def locks = state.locks

		settings.lockDevices.each { dni ->
            def d = getChildDevice(dni)
            if(!d) {
            	def newLock
                newLock = locks.find { ("TC-${settings.automationDeviceId}-${it.value.id}") == dni }
				
                log.debug "dni: ${dni}"
                log.debug "newLock: ${newLock}"
                
				d = addChildDevice("jhstroebel", "TotalConnect Lock", dni, null /*Hub ID*/, [name: "Device.${dni}", label: "${newLock?.value.name}", completedSetup: true])
			}//if it doesn't already exist
       	}//for each selected lock
	}//if there are lockDevices
}//addDevices()

private removeChildDevices(delete) {
    delete.each {
        deleteChildDevice(it.deviceNetworkId)
    }
}

/////////////////////////////////////
// CHILD DEVICE METHODS
/////////////////////////////////////

// Arm Function. Performs arming function
def armAway(childDevice) {
    log.debug "TotalConnect2.0 SM: Executing 'armAway'"
	tcCommandAsync("ArmSecuritySystem", [SessionID: state.token, LocationID: settings.locationId, DeviceID: settings.securityDeviceId, ArmType: 0, UserCode: '-1'])
	//we do nothing with response (its almost useless on arm, they want you to poll another command to check for success)

/* this code may not make sense... Alarm shows as armed during countdown.  Maybe push arming, then push status?  Also what happens if it doesn't arm?  this runs forever?
 * Also can't use pause(60000), it will exceed the 20 second method execution time... maybe try a runIn() in the device handler to refresh status
 
    pause(60000) //60 second pause for arming countdown
    def alarmCode = alarmPanelStatus()
    
    while(alarmCode != 10201) {
    	pause(3000) // 3 second pause to retry alarm status
        alarmCode = alarmPanelStatus()
    }//while alarm has not armed

	//log.debug "Home is now Armed successfully" 
    sendEvent(it, [name: "status", value: "Armed Away", displayed: "true", description: "Refresh: Alarm is Armed Away"])
*/
}//armaway

def armAwayInstant(childDevice) {
    log.debug "TotalConnect2.0 SM: Executing 'armAwayInstant'"
    tcCommandAsync("ArmSecuritySystem", [SessionID: state.token, LocationID: settings.locationId, DeviceID: settings.securityDeviceId, ArmType: 3, UserCode: '-1'])
	//we do nothing with response (its almost useless on arm, they want you to poll another command to check for success)

/*	
    def metaData = panelMetaData(token, locationId) // Get AlarmCode
	while( metaData.alarmCode != 10205 ){ 
		pause(3000) // 3 Seconds Pause to relieve number of retried on while loop
		metaData = panelMetaData(token, locationId)
	}  
	//log.debug "Home is now Armed successfully" 
	sendPush("Home is now Armed successfully")
*/
}//armaway

def armStay(childDevice) {        
	log.debug "TotalConnect2.0 SM: Executing 'armStay'"
	tcCommandAsync("ArmSecuritySystem", [SessionID: state.token, LocationID: settings.locationId, DeviceID: settings.securityDeviceId, ArmType: 1, UserCode: '-1'])

/* 	
    def metaData = panelMetaData(token, locationId) // Gets AlarmCode
	while( metaData.alarmCode != 10203 ){ 
		pause(3000) // 3 Seconds Pause to relieve number of retried on while loop
		metaData = panelMetaData(token, locationId)
	} 
	//log.debug "Home is now Armed for Night successfully"     
	sendPush("Home is armed in Night mode successfully")
*/
}//armstay

def armStayInstant(childDevice) {        
	log.debug "TotalConnect2.0 SM: Executing 'armStayInstant'"
    tcCommandAsync("ArmSecuritySystem", [SessionID: state.token, LocationID: settings.locationId, DeviceID: settings.securityDeviceId, ArmType: 2, UserCode: '-1'])

/* 	
    def metaData = panelMetaData(token, locationId) // Gets AlarmCode
	while( metaData.alarmCode != 10209 ){ 
		pause(3000) // 3 Seconds Pause to relieve number of retried on while loop
		metaData = panelMetaData(token, locationId)
	} 
	//log.debug "Home is now Armed for Night successfully"     
	sendPush("Home is armed in Night mode successfully")
*/
}//armstay

def disarm(childDevice) {
	log.debug "TotalConnect2.0 SM: Executing 'disarm'"
    tcCommandAsync("DisarmSecuritySystem", [SessionID: state.token, LocationID: settings.locationId, DeviceID: settings.securityDeviceId, UserCode: '-1'])

/*   	
    def metaData = panelMetaData(token, locationId) // Gets AlarmCode
    while( metaData.alarmCode != 10200 ){ 
		pause(3000) // 3 Seconds Pause to relieve number of retried on while loop
		metaData = panelMetaData(token, locationId)
	}
	// log.debug "Home is now Disarmed successfully"   
	sendPush("Home is now Disarmed successfully")
*/
}//disarm

def bypassSensor(childDevice) {
    def childDeviceInfo = childDevice.getDeviceNetworkId().split("-") //takes deviceId & zoneId from deviceNetworkID in format "TC-DeviceID-SwitchID"
    def deviceId = childDeviceInfo[1]
	def zoneId = childDeviceInfo[2]
    
    log.debug "TotalConnect2.0 SM: Bypassing Sensor"
	log.debug "Bypassing Zone: ${zoneId}"

	tcCommandAsync("Bypass", [SessionID: state.token, LocationID: settings.locationId, DeviceID: deviceId, Zone: zoneId, UserCode: '-1'])
}//bypassSensor

def controlSwitch(childDevice, int switchAction) {		   
	def childDeviceInfo = childDevice.getDeviceNetworkId().split("-") //takes deviceId & switchId from deviceNetworkID in format "TC-DeviceID-SwitchID"
    def deviceId = childDeviceInfo[1]
	def switchId = childDeviceInfo[2]

	tcCommandAsync("ControlASwitch", [SessionID: state.token, DeviceID: deviceId, SwitchID: switchId, SwitchAction: switchAction])
}//controlSwitch

def controlLock(childDevice, int lockDesiredState) {		   
	def childDeviceInfo = childDevice.getDeviceNetworkId().split("-") //takes deviceId & switchId from deviceNetworkID in format "TC-DeviceID-SwitchID"
    def deviceId = childDeviceInfo[1]
	def lockId = childDeviceInfo[2]

	tcCommandAsync("ControlALock", [SessionID: state.token, DeviceID: deviceId, LockID: lockId, LockDesiredState: lockDesiredState, AuthorizingCode: ""])
}//controlSwitch

def pollChildren(childDevice = null) {
	//log.debug "pollChildren() - forcePoll: ${state.forcePoll}, lastPoll: ${state.lastPoll}, now: ${now()}"
    
	if(!isTokenValid())
    {
    	log.error "Token is likely expired.  Check Keep alive function in SmartApp"
        login()
        pause(1000)
    }//check if token is likely still valid or login.  Might add a sendCommand(command) method and check before sending any commands...
   
    if(childDevice == null) {
        log.debug "pollChildren: No child device passed in, will update all devices"
        //update all devices (after checking that they exist)
        if(settings.alarmDevice) {
        	//update alarm
            tcCommandAsync("GetPanelMetaDataAndFullStatusEx", [SessionID: state.token, LocationID: settings.locationId, LastSequenceNumber: 0, LastUpdatedTimestampTicks: 0, PartitionID: 1]) //This updates panel status
            //state.alarmStatus = alarmPanelStatus()
            //updateAlarmStatus()
        }
    	if(settings.zoneDevices) {
        	//update zoneDevices
			tcCommandAsync("GetZonesListInStateEx", [SessionID: state.token, LocationID: settings.locationId, PartitionID: 1, ListIdentifierID: 0])
            //state.zoneStatus = zoneStatus()
            //updateZoneStatuses()
        }
        if(settings.automationDevices || settings.thermostatDevices || settings.lockDevices) {
        	//update automationDevices
            tcCommandAsync("GetAllAutomationDeviceStatusEx", [SessionID: state.token, DeviceID: settings.automationDeviceId, AdditionalInput: ''])
            //state.switchStatus = automationDeviceStatus()
            //updateAutomationStatuses()
        }//automation devices are 1 call... if any exist update all 3 types
        
		//updateStatuses()

    }//check device type and update all of that type only (scheduled polling)
    else {
    	log.debug "pollChildren: childDevice: ${childDevice} passed in"
        def childDeviceInfo = childDevice.getDeviceNetworkId().split("-") //takes deviceId & subDeviceId from deviceNetworkID in format "TC-DeviceID-SubDeviceID"
        def deviceId = childDeviceInfo[1]
                
        if(childDeviceInfo.length == 2) {
        	log.debug "Running Security Panel update only"
            //its a security panel
            tcCommandAsync("GetPanelMetaDataAndFullStatusEx", [SessionID: state.token, LocationID: settings.locationId, LastSequenceNumber: 0, LastUpdatedTimestampTicks: 0, PartitionID: 1]) //This updates panel status
            //state.alarmStatus = alarmPanelStatus()
            //updateAlarmStatus()
        } else if(deviceId == settings.securityDeviceId) {
        	log.debug "Running Zone Sensor update(s) only"        	
            //its a zone sensor
			tcCommandAsync("GetZonesListInStateEx", [SessionID: state.token, LocationID: settings.locationId, PartitionID: 1, ListIdentifierID: 0])
			//state.zoneStatus = zoneStatus()
            //updateZoneStatuses()
        } else if(deviceId == settings.automationDeviceId) {
        	log.debug "Running Automation Device update(s) only"
        	//its an automation device (for now below works, but when thermostats and locks are added, need more definition)
            tcCommandAsync("GetAllAutomationDeviceStatusEx", [SessionID: state.token, DeviceID: settings.automationDeviceId, AdditionalInput: ''])
            //state.switchStatus = automationDeviceStatus()
            //updateAutomationStatuses()
        }
        else {
        	log.error "deviceNetworkId is not formatted as expected.  ID: ${childDevice.getDeviceNetworkId()}"
        }
	}//if childDevice is passed in (on demand refresh)
	
    
/* Code stolen from ecobee    
   // Check to see if it is time to do an full poll to the Ecobee servers. If so, execute the API call and update ALL children
    def timeSinceLastPoll = (atomicState.forcePoll == true) ? 0 : ((now() - atomicState.lastPoll?.toDouble()) / 1000 / 60) 
    LOG("Time since last poll? ${timeSinceLastPoll} -- atomicState.lastPoll == ${atomicState.lastPoll}", 3, child, "info")
    
    if ( (atomicState.forcePoll == true) || ( timeSinceLastPoll > getMinMinBtwPolls().toDouble() ) ) {
    	// It has been longer than the minimum delay OR we are doing a forced poll
        LOG("Calling the Ecobee API to fetch the latest data...", 4, child)
    	pollEcobeeAPI(getChildThermostatDeviceIdsString())  // This will update the values saved in the state which can then be used to send the updates
	} else {
        LOG("pollChildren() - Not time to call the API yet. It has been ${timeSinceLastPoll} minutes since last full poll.", 4, child)
        generateEventLocalParams() // Update any local parameters and send
    }
*/// pollChildren from Ecobee Connect Code
}//pollChildren

def updateAlarmStatus() {
    try {
		if(state.alarmStatus) {
			def deviceID = "TC-${settings.securityDeviceId}"
			def d = getChildDevice(deviceID)
			def currentStatus = state.alarmStatus
			            
			switch(currentStatus) {
				case "10211": //technically this is Disarmed w/ Zone Bypassed
				case "10200":
                    //log.debug "Polled Status is: Disarmed"
                    if(d.currentStatus != "Disarmed") {
                       	sendEvent(d, [name: "status", value: "Disarmed", displayed: "true", description: "Refresh: Alarm is Disarmed"]) 
						if(settings.alarmDevice && settings.shmIntegration) {
    						sendLocationEvent(name: "alarmSystemStatus", value: "off")
						}//if integration is enabled, update SHM alarm status
                    }//if current status isn't Disarmed
                    break
				case "10202": //technically this is Armed Away w/ Zone Bypassed
				case "10201":
					//log.debug "Polled Status is: Armed Away"
					if(d.currentStatus != "Armed Away") {
						sendEvent(d, [name: "status", value: "Armed Away", displayed: "true", description: "Refresh: Alarm is Armed Away"])  
						if(settings.alarmDevice && settings.shmIntegration) {
    						sendLocationEvent(name: "alarmSystemStatus", value: "away")
						}//if integration is enabled, update SHM alarm status
                    }//if current status isn't Armed Away
					break
				case "10204": //technically this is Armed Stay w/ Zone Bypassed
				case "10203":
					//log.debug "Polled Status is: Armed Stay"
					if(d.currentStatus != "Armed Stay") {
						sendEvent(d, [name: "status", value: "Armed Stay", displayed: "true", description: "Refresh: Alarm is Armed Stay"])   
						if(settings.alarmDevice && settings.shmIntegration) {
    						sendLocationEvent(name: "alarmSystemStatus", value: "stay")
						}//if integration is enabled, update SHM alarm status
                    }//if current status isn't Armed Stay
					break
				case "10206": //technically this is Armed Away - Instant w/ Zone Bypassed
				case "10205":
					//log.debug "Polled Status is: Armed Away - Instant"
					if(d.currentStatus != "Armed Stay - Instant") {
						sendEvent(d, [name: "status", value: "Armed Away - Instant", displayed: "true", description: "Refresh: Alarm is Armed Away - Instant"])    
						if(settings.alarmDevice && settings.shmIntegration) {
    						sendLocationEvent(name: "alarmSystemStatus", value: "away")
						}//if integration is enabled, update SHM alarm status
                    }//if current status isn't Armed Away - Instant
					break
				case "10210": //technically this is Armed Stay - Instant w/ Zone Bypassed
				case "10209":
					//log.debug "Polled Status is: Armed Stay - Instant"
					if(d.currentStatus != "Armed Stay - Instant") {
						sendEvent(d, [name: "status", value: "Armed Stay - Instant", displayed: "true", description: "Refresh: Alarm is Armed Stay - Instant"])    
						if(settings.alarmDevice && settings.shmIntegration) {
    						sendLocationEvent(name: "alarmSystemStatus", value: "stay")
						}//if integration is enabled, update SHM alarm status
                    }//if current status isn't Armed Stay - Instant
					break                            
				case "10218":
					//log.debug "Polled Status is: Armed Night Stay"
					if(d.currentStatus != "Armed Night Stay") {
						sendEvent(d, [name: "status", value: "Armed Night Stay", displayed: "true", description: "Refresh: Alarm is Armed Night Stay"])    
						if(settings.alarmDevice && settings.shmIntegration) {
    						sendLocationEvent(name: "alarmSystemStatus", value: "stay")
						}//if integration is enabled, update SHM alarm status (calling Armed Night Stay as Stay)
                    }//if current status isn't Armed Night Stay
					break
				case "10307":
					log.debug "Polled Status is: Arming"
					if(d.currentStatus != "Arming") {
						sendEvent(d, [name: "status", value: "Arming", displayed: "true", description: "Refresh: Alarm is Arming"])
                        runIn(3, pollChildren(d)) //This updates the panel status again 3 seconds later
                    }//Probably unneeded, this state last for a very short time
					break 
				case "10308":
					log.debug "Polled Status is: Disarming"
					if(d.currentStatus != "Disarming") {
						sendEvent(d, [name: "status", value: "Disarming", displayed: "true", description: "Refresh: Alarm is Disarming"])
						runIn(3, pollChildren(d)) //This updates the panel status again 3 seconds later
					}//Probably unneeded, this likely never happens
					break
				default:
					log.error "Alarm Status returned an irregular value " + currentStatus
					break
			}//switch(currentStatus) for alarm status
							
			switch(currentStatus) {
				//cases where zone is bypassed
				case "10211": //Disarmed w/ Zone Bypassed
				case "10202": //Armed Away w/ Zone Bypassed
				case "10204": //Armed Stay w/ Zone Bypassed
				case "10206": //Armed Away - Instant w/ Zone Bypassed
				case "10210": //Armed Stay - Instant w/ Zone Bypassed
					if(d.currentZonesBypassed != "true") {
						sendEvent(d, [name: "zonesBypassed", value: "true", displayed: "true", description: "Refresh: Alarm zones are bypassed"]) }
					break
				
				//cases where zone is not bypassed
				case "10200": //Disarmed
				case "10201": //Armed Away
				case "10203": //Armed Stay
				case "10205": //Armed Away - Instant
				case "10209": //Armed Stay - Instant
				case "10218": //Armed Night Stay
					if(d.currentZonesBypassed != "false") {
                       	sendEvent(d, [name: "zonesBypassed", value: "false", displayed: "true", description: "Refresh: Alarm zones are not bypassed"]) }
					break
				
				default:
					//log.error "Alarm Status returned an irregular value " + currentStatus
					break
			}//switch(currentStatus) for zonesBypassed (this is way shorter than dealing with all cases in 1 switch
			
			sendEvent(name: "refresh", value: "true", displayed: "true", description: "Alarm Refresh Successful") 
		} else {
			log.error "Alarm Code does not exist"
		}//alarm code doesn't exist
    } catch (e) {
    	log.error("Error Occurred Updating Alarm: " + e)
    }// try/catch block
} //updateAlarmStatus()

def updateAutomationStatuses() {    
	def children = getChildDevices()    
	def automationChildren = children?.findAll { it.deviceNetworkId.startsWith("TC-${settings.automationDeviceId}-") }
        
	automationChildren.each { 
		try {
			//log.debug "(Switch) SmartThings State is: " + it.currentStatus
			String id = it.getDeviceNetworkId().split("-")[2] //takes switch/lock/thermostatId from deviceNetworkID in format "TC-DeviceID-ID"
            
			if(state.switchStatus.containsKey(id)) {
            	def events = []
				def switchState = state.switchStatus.get(id)
				//log.debug "(Switch) Polled State is: ${switchState}"
                    
                switch(switchState) {
                    case "0":
                        //log.debug "Status is: Closed"
                        if(it.currentStatus != "closed") {
                            events << [name: "status", value: "closed", displayed: "true", description: "Refresh: Garage Door is Closed", isStateChange: "true"]
                            events << [name: "switch", value: "off", displayed: "false", description: "Refresh: Garage Door is Closed", isStateChange: "true"]
                            //sendEvent(it, [name: "status", value: "closed", displayed: "true", description: "Refresh: Garage Door is Closed", isStateChange: "true"])
                        }
                        break
                    case "1":
                        //log.debug "Status is: Open"
                        if(it.currentStatus != "open") {
                        	events << [name: "status", value: "closed", displayed: "true", description: "Refresh: Garage Door is Closed", isStateChange: "true"]
							events << [name: "switch", value: "on", displayed: "false", description: "Refresh: Garage Door is Closed", isStateChange: "true"]
                            //sendEvent(it, [name: "status", value: "open", displayed: "true", description: "Refresh: Garage Door is Open", isStateChange: "true"])
                        }
                        break
                    default:
                        log.error "Attempted to update switchState to ${switchState}. Only valid states are 0 or 1."
                        break
                }//switch(switchState)
                
                it.generateEvent(events)
            } else if(state.lockStatus?.containsKey(id)) {
            	def events = []
				def lockState = state.lockStatus.get(id)
				//log.debug "(Lock) Polled State is: ${lockState}"
                    
                switch(lockState) {
                    case "0":
                        //log.debug "Status is: Unlocked"
                        if(it.currentStatus != "unlocked") {
                            events << [name: "status", value: "unlocked", displayed: "true", description: "Refresh: Lock is Unlocked", isStateChange: "true"]
                            events << [name: "switch", value: "off", displayed: "false", description: "Refresh: Lock is Unlocked", isStateChange: "true"]
                            //sendEvent(it, [name: "status", value: "closed", displayed: "true", description: "Refresh: Lock is Unlocked", isStateChange: "true"])
                        }
                        break
                    case "1":
                        //log.debug "Status is: Locked"
                        if(it.currentStatus != "locked") {
                        	events << [name: "status", value: "locked", displayed: "true", description: "Refresh: Lock is Locked", isStateChange: "true"]
							events << [name: "switch", value: "on", displayed: "false", description: "Refresh: Lock is Locked", isStateChange: "true"]
                            //sendEvent(it, [name: "status", value: "open", displayed: "true", description: "Refresh: Lock is Locked", isStateChange: "true"])
                        }
                        break
                    default:
                        log.error "Attempted to update lockState to ${lockState}. Only valid states are 0 or 1."
                        break
                }//switch(lockState)
                
                it.generateEvent(events)
            }//if(state.lockState.containsKey(lockId)
            else {
                log.error "${id} does not exist as a SwitchID or LockID."
            }
        } catch (e) {
            log.error("Error Occurred Updating Device " + it.displayName + ", Error " + e)
        }// try/catch block
    }//switchChildren.each    
} //updateSwitchStatus()

def updateZoneStatuses() {
	def children = getChildDevices()
    def zoneChildren = children?.findAll { it.deviceNetworkId.startsWith("TC-${settings.securityDeviceId}-") }

	zoneChildren.each { 
		try {
			String zoneId = it.getDeviceNetworkId().split("-")[2] //takes zoneId from deviceNetworkID in format "TC-DeviceID-ZoneID"
			String zoneName = it.getDisplayName()
			//log.debug "Zone ${zoneId} - ${zoneName}"
			//log.debug "(Sensor) SmartThings State is: " + it.currentContact
                
			if(state.zoneStatus.containsKey(zoneId)) {
				String currentStatus = state.zoneStatus.get(zoneId)
				//log.debug "(Sensor) Polled State is: " + currentStatus
				def events = []
                    
				switch(currentStatus) {
					case "0":                    
						//log.debug "Zone ${zoneId} is OK"
						events << [name: "contact", value: "closed"]
						//sendEvent(it, [name: "status", value: "closed", displayed: "true", description: "Refresh: Zone is closed", isStateChange: "true"])
						if(it.hasCapability("motionSensor")) {
							events << [name: "motion", value: "inactive"]
							//sendEvent(it, [name: "motion", value: "active", displayed: "true", description: "Refresh: No Motion detected in Zone", isStateChange: "true"])
						}//if motion sensor, update that as well (maybe do this in the device handler?)
						break
					case "1":                    
						//log.debug "Zone ${zoneId} is Bypassed"
						events << [name: "contact", value: "bypassed"]
						//sendEvent(it, [name: "contact", value: "bypassed", displayed: "true", description: "Refresh: Zone is bypassed", isStateChange: "true"])
						break
					case "2":                    
						//log.debug "Zone ${zoneId} is Faulted"
						events << [name: "contact", value: "open"]
						//sendEvent(it, [name: "contact", value: "open", displayed: "true", description: "Refresh: Zone is Faulted", isStateChange: "true"])
						if(it.hasCapability("motionSensor")) {
							events << [name: "motion", value: "active"]
							//sendEvent(it, [name: "motion", value: "active", displayed: "true", description: "Refresh: Motion detected in Zone", isStateChange: "true"])
						}//if motion sensor, update that as well (maybe do this in the device handler?)
						break
					case "8":                    
						//log.debug "Zone ${zoneId} is Troubled"
						events << [name: "contact", value: "trouble"]
						//sendEvent(it, [name: "contact", value: "trouble", displayed: "true", description: "Refresh: Zone is Troubled", isStateChange: "true"])
						break
					case "16":                    
						//log.debug "Zone ${zoneId} is Tampered"
						events << [name: "contact", value: "tampered"]
						//sendEvent(it, [name: "contact", value: "tampered", displayed: "true", description: "Refresh: Zone is Tampered", isStateChange: "true"])
						break
					case "32":                    
						//log.debug "Zone ${zoneId} is Failed"
						events << [name: "contact", value: "failed"]
						//sendEvent(it, [name: "contact", value: "failed", displayed: "true", description: "Refresh: Zone is Failed", linkText: "Zone ${zoneId} - ${zoneName}", isStateChange: "true"])
						break
					default:
						log.error "Zone ${zoneId} returned an unexpected value.  ZoneStatus: ${currentStatus}"
						break
                }//switch(currentStatus)
					
                it.generateEvent(events)
			}//if(state.zoneStatus.containsKey(zoneId)) 
            else {
				log.debug "ZoneId ${zoneId} does not exist"
			}//else
      	} catch (e) {
      		log.error("Error Occurred Updating Sensor "+it.displayName+", Error " + e)
      	}// try/catch
	}//zoneChildren.each
	log.debug "Finished Updating"
	return true
}//updateZoneStatuses()

/////////////////////////////////////
// ASYNC Code & Methods
/////////////////////////////////////

def tcCommandAsync(String path, Map body, Integer retry = 0) {
	String stringBody = ""
    
    body.each { k, v ->
    	if(!(stringBody == "")) {
        	stringBody += "&" }            
        stringBody += "${k}=${v}"
    }//convert Map to String

	//log.debug "stringBody: ${stringBody}"

    def params = [
		uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/",	
		path: path,
    	body: stringBody,
        requestContentType: "application/x-www-form-urlencoded",
        contentType: "application/xml"
    ]
    
    def handler
        
    switch(path) {
    	case "AuthenticateUserLogin":
        	handler = "login"
            break
        case "Logout":
        	handler = "logout"
            break
        case "GetSessionDetails":
        	if(state.setup) {
        		handler = "sessionDetails"
            } else {
            	handler = "keepAlive"
            }//we're looking to keep authentication alive
            break
        case "GetPanelMetaDataAndFullStatusEx":
            if(state.setup) {
            	handler = "sensorDiscovery"
            } else {
            	handler = "panel"
            }//we're looking to update automation devices
            break
        case "GetZonesListInStateEx":
        	handler = "zone"
            break
        case "GetAllAutomationDeviceStatusEx":
        	if(state.setup) {
            	handler = "automationDiscovery"
            } else {
            	handler = "automation"
            }//we're looking to update automation devices
            break
        default:
        	handler = "none"
            break
    }//define handler based on method called
    
    def data = [
    	path: path,
        body: stringBody,
        handler: handler,
        retry: retry
    ] //Data for Async Command.  Params to retry, handler to handle, and retry count if needed
    
    try {
    	asynchttp_v1.post('asyncResponse', params, data)
        //log.debug "Sent asynchhttp_v1.post(asyncResponse, ${params}, ${data})"
    } catch (e) {
    	log.error "Something unexpected went wrong in tcCommandAsync: $e"
	}//try / catch for asynchttpPost
}//async post command

def asyncResponse(response, data) {
    if (response.hasError()) {
        log.debug "error response data: $response.errorData"
        try {
            // exception thrown if xml cannot be parsed from response
            log.debug "error response xml: $response.errorXml"
        } catch (e) {
            log.warn "error parsing xml: $e"
        }
        try {
            // exception thrown if json cannot be parsed from response
            log.debug "error response json: $response.errorJson"
        } catch (e) {
            log.warn "error parsing json: $e"
        }
    }
	
    response = response.getXml()
 
    try {
    	//validate response
    	def resultCode = response.ResultCode
        def resultData = response.ResultData
        
        //log.debug "ResultCode: ${resultCode}, ResultData: ${resultData}"
        
        switch(resultCode) {
        	case "0": //Successful Command
            case "4500": //Successful Command for Arm Action
				state.tokenRefresh = now() //we ran a successful command, that will keep the token alive
                
                //log.debug "Handler: ${data.get('handler')}"
				switch(data.get('handler')) {
					//authentication cases
                    case "login":
                        state.token = response?.SessionID.toString()
                        state.tokenRefresh = now()
						String refreshDate = new Date(state.tokenRefresh).format("EEE MMM d HH:mm:ss Z",  location.timeZone)
						log.debug "Smart Things has logged in at ${refreshDate} SessionID: ${state.token}"
                        break
                    case "logout":
						log.debug "Smart Things has successfully logged out"
						state.token = null
						state.tokenRefresh = null
                        break
                    //setup cases
                    case "sessionDetails":
                        getPanelInfo(response)
                        break
                    case "sensorDiscovery":
						discoverSensors(response)
                    	break
                    case "automationDiscovery":
						discoverSwitches(response)
						discoverThermostats(response)
						discoverLocks(response)
                    	break
                    //update cases
                    case "panel":
                        state.alarmStatus = getAlarmStatus(response)
                        updateAlarmStatus()
                        break
                    case "zone":
                        state.zoneStatus = getZoneStatus(response)
                        updateZoneStatuses()
                        break
                    case "automation":
                        state.switchStatus = getAutomationDeviceStatus(response)
						//state.lockStatus = get
                        updateAutomationStatuses()
                        break
                    case "keepAlive":
                        String refreshDate = new Date(state.tokenRefresh).format("EEE MMM d HH:mm:ss Z",  location.timeZone)
						log.debug "Session kept alive at ${refreshDate}"
                        break
                    default:
                        //if its not an update method or keepAlive we don't return anything
                        return
                        break
                }//switch(data)
                break
            case "-102":
	        	//this means the Session ID is invalid, needs to login and try again
    	        log.error "Command Type: ${data} failed with ResultCode: ${resultCode} and ResultData: ${resultData}"
        	    log.debug "Attempting to refresh token and try again"
            	login()
            	pause(1000) //pause should allow login to complete before trying again.
				tcCommandAsync(data.get('path'), data.get('body')) //we don't send retry as 1 since it was a login failure
                break
			case "4101": //We are unable to connect to the security panel. Please try again later or contact support
            case "4108": //Panel not connected with Virtual Keypad. Check Power/Communication failure
            case "-4002": //The specified location is not valid
            case "-4108": //Cannot establish a connection at this time. Please contact your Security Professional if the problem persists.
            default: //Other Errors 
				log.error "Command Type: ${data} failed with ResultCode: ${resultCode} and ResultData: ${resultData}"
                /* Retry causes goofy issues...		
                    if(retry == 0) {
                        pause(2000) //pause 2 seconds (otherwise this hits our rate limit)
                        retry += 1
                        tcCommandAsync(data.get('path'), data.get('body'), retry)
                    }//retry after 3 seconds if we haven't retried before
                */      
                break
		}//switch
	} catch (SocketTimeoutException e) {
        //identify a timeout and retry?
		log.error "Timeout Error: $e"
	/* Retry causes goofy issues...		
        if(retry == 0) {
        	pause(2000) //pause 2 seconds (otherwise this hits our rate limit)
           	retry += 1
            tcCommandAsync(data.get('path'), data.get('body'), retry)
		}//retry after 5 seconds if we haven't retried before
	*/
    } catch (e) {
    	log.error "Something unexpected went wrong in asyncResponse: $e"
	}//try / catch for httpPost
}//asyncResponse

def getPanelInfo(response) {
	//getLocationID & DeviceIDs in same call for setup
	def locationId
	def locationName
	state.locationMap = [:]
	
	String deviceClassId
	String deviceId
	state.deviceMap = [:]
						
	response.Locations.LocationInfoBasic.each { LocationInfoBasic ->
		def tempMap = [:]
		locationName = LocationInfoBasic.LocationName
		locationId = LocationInfoBasic.LocationID
		state.locationMap["${locationName}"] = "${locationId}"
		//additional code to save DeviceIDs
		LocationInfoBasic.DeviceList.DeviceInfoBasic.each { DeviceInfoBasic ->
			deviceClassId = DeviceInfoBasic.DeviceClassID
			deviceId = DeviceInfoBasic.DeviceID
			tempMap.put(deviceClassId, deviceId)
		}//iterate throught DeviceIDs
		state.deviceMap.put(locationId, tempMap)
	}//LocationInfoBasic.each
}//getPanelInfo()

//Discovers Sensors from cloud
def discoverSensors(response) {
    def sensors = [:]

	response.PanelMetadataAndStatus.Zones.ZoneInfo.each { ZoneInfo ->
		//zoneID = ZoneInfo.'@ZoneID'
		//zoneName = ZoneInfo.'@ZoneDescription'
    	//zoneType //needs to come from input
		sensors[ZoneInfo.'@ZoneID'] = [id: "${ZoneInfo.'@ZoneID'}", name: "${ZoneInfo.'@ZoneDescription'}"]
	}//iterate through zones				

	log.debug "TotalConnect2.0 SM:  ${sensors.size()} sensors found"
    //log.debug sensors

	state.sensors = sensors
} //Should discover sensor information and save to state

// Discovers Switch Devices (Switches, Dimmmers, & Garage Doors)
def discoverSwitches(response) {
    def switches = [:]
	
   	response.AutomationData.AutomationSwitch.SwitchInfo.each { SwitchInfo ->
		//switchID = SwitchInfo.SwitchID
		//switchName = SwitchInfo.SwitchName
		//switchType = SwitchInfo.SwitchType
		//switchIcon = SwitchInfo.SwitchIconID // 0-Light, 1-Switch, 255-Garage Door, maybe use for default?
		//switchState = SwitchInfo.SwitchState // 0-Off, 1-On, maybe set initial value?
		//switchLevel = SwitchInfo.SwitchLevel // 0-99, maybe to set intial value?
		switches[SwitchInfo.SwitchID] = [id: "${SwitchInfo.SwitchID}", name: "${SwitchInfo.SwitchName}", type: "${SwitchInfo.SwitchType}"] //use "${var}" to typecast into String
	}//iterate through Switches				

	log.debug "TotalConnect2.0 SM:  ${switches.size()} switches found"
    //log.debug switches

	state.switches = switches
} //Should discover switch information and save to state (could combine all automation to turn 3 calls into 1 or pass XML section for each type to discovery...)

// Discovers Thermostat Devices
def discoverThermostats(response) {
	def thermostats = [:]

    response.AutomationData.AutomationThermostat.ThermostatInfo.each { ThermostatInfo ->
		//thermostatID = ThermostatInfo.ThermostatID
		//thermostatName = ThermostatInfo.ThermostatName
        thermostats[ThermostatInfo.ThermostatID] = [id: "${ThermostatInfo.ThermostatID}", name: "${ThermostatInfo.ThermostatName}"] //use "${var}" to typecast into String
	}//ThermostatInfo.each    							

	log.debug "TotalConnect2.0 SM:  ${thermostats.size()} thermostats found"
    //log.debug thermostatMap

	state.thermostats = thermostats
} //Should return thermostat information

// Discovers Lock Devices
def discoverLocks(response) {
	def locks = [:]

   	response.AutomationData.AutomationLock.LockInfo_Transitional.each { LockInfo_Transitional ->
		//lockID = LockInfo_Transitional.LockID
		//lockName = LockInfo_Transitional.LockName
		locks[LockInfo_Transitional.LockID] = [id: "${LockInfo_Transitional.LockID}", name: "${LockInfo_Transitional.LockName}"] //use "${var}" to typecast into String
	}//iterate through Locks
    
	log.debug "TotalConnect2.0 SM:  ${locks.size()} locks found"
    //log.debug locks

	state.locks = locks
} //Should discover locks information and save to state (could combine all automation to turn 3 calls into 1 or pass XML section for each type to discovery...)

// Gets Panel Metadata.
def getAlarmStatus(response) {
	String alarmCode
   
	alarmCode = response.PanelMetadataAndStatus.Partitions.PartitionInfo.ArmingState

	state.alarmStatusRefresh = now()
	return alarmCode
} //returns alarmCode

Map getZoneStatus(response) {
    String zoneID
    String zoneStatus
    def zoneMap = [:]
	try {
        response?.ZoneStatus.Zones.ZoneStatusInfoEx.each
        {
            ZoneStatusInfoEx ->
                zoneID = ZoneStatusInfoEx.'@ZoneID'
                zoneStatus = ZoneStatusInfoEx.'@ZoneStatus'
                //bypassable = ZoneStatusInfoEx.'@CanBeBypassed' //0 means no, 1 means yes
                zoneMap.put(zoneID, zoneStatus)
        }//each Zone 

        //log.debug "ZoneNumber: ZoneStatus " + zoneMap
	} catch (e) {
      	log.error("Error Occurred Updating Zones: " + e)
	}// try/catch block
	
    if(zoneMap) {
    	state.zoneStatusRefresh = now()
    	return zoneMap
    } else {
    	return state.zoneStatus
    }//if zoneMap is empty, return current state as a failsafe and don't update zoneStatusRefresh
} //Should return zone information

// Gets Automation Device Status
Map getAutomationDeviceStatus(response) {
	String switchID
	String switchState
    String switchType
    String switchLevel
    Map automationMap = [:]

	try {
        response.AutomationData.AutomationSwitch.SwitchInfo.each
        {
            SwitchInfo ->
                switchID = SwitchInfo.SwitchID
                switchState = SwitchInfo.SwitchState
                //switchType = SwitchInfo.SwitchType
                //switchLevel = SwitchInfo.SwitchLevel
                automationMap.put(switchID,switchState)
			/* Future format to store state information	(maybe store by TC-deviceId-switchId for ease of retrevial?)
                if(switchType == "2") {
                	automationMap[SwitchInfo.SwitchID] = [id: "${SwitchInfo.SwitchID}", switchType: "${SwitchInfo.SwitchType}", switchState: "${SwitchInfo.SwitchState}", switchLevel: "${SwitchInfo.SwitchLevel}"]
                } else {
                	automationMap[SwitchInfo.SwitchID] = [id: "${SwitchInfo.SwitchID}", switchType: "${SwitchInfo.SwitchType}", switchState: "${SwitchInfo.SwitchState}"]
			*/
        }//SwitchInfo.each

        //log.debug "SwitchID: SwitchState " + automationMap
	/*		
		response.AutomationData.AutomationThermostat.ThermostatInfo.each
        {
            ThermostatInfo ->
                automationMap[ThermostatInfo.ThermostatID] = [
                    thermostatId: ThermostatInfo.ThermostatID,
                    currentOpMode: ThermostatInfo.CurrentOpMode,
                    thermostatMode: ThermostatInfo.ThermostatMode,
                    thermostatFanMode: ThermostatInfo.ThermostatFanMode,
                    heatSetPoint: ThermostatInfo.HeatSetPoint,
                    coolSetPoint: ThermostatInfo.CoolSetPoint,
                    energySaveHeatSetPoint: ThermostatInfo.EnergySaveHeatSetPoint,
                    energySaveCoolSetPoint: ThermostatInfo.EnergySaveCoolSetPoint,
                    temperatureScale: ThermostatInfo.TemperatureScale,
                    currentTemperture: ThermostatInfo.CurrentTemperture,
                    batteryState: ThermostatInfo.BatteryState]
        }//ThermostatInfo.each
    */
        
        String lockID
        String lockState
        String batteryState
    	Map automationMap2 = [:]
        
		response.AutomationData.AutomationLock.LockInfo_Transitional.each
        {
            LockInfo_Transitional ->
                lockID = LockInfo_Transitional.LockID
                lockState = LockInfo_Transitional.LockState
                batteryState = LockInfo_Transitional.BatteryState
                automationMap2.put(lockID,lockState)
        }//LockInfo_Transitional.each
        
        state.lockStatus = automationMap2
    
	} catch (e) {
      	log.error("Error Occurred Updating Automation Devices: " + e)
	}// try/catch block
	
    if(automationMap) {
    	state.automationStatusRefresh = now()
    	return automationMap
    } else {
    	return state.automationStatus
    }//if automationMap is empty, return current state as a failsafe and don't update automationStatusRefresh
} //Should return switch state information for all SwitchIDs