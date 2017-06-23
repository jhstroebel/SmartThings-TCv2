/**
 *  TotalConnect
 *
 *  Version: v0.1
 *   Changes [June 6th, 2017]
 *  	- Started from code by mhatrey @ https://github.com/mhatrey/TotalConnect/blob/master/TotalConnect.groovy 	
 *  	- Modified locationlist to work (as List)
 *      - Added Autodiscover for Automation and Security Panel DeviceIDs
 *      - Added Iteration of devices to add (did all automation devices even though I only want a non-zwave garage door)
 *   	- Removed Success Page (unneccesary)
 * Version: v1.0
 *	Changes [Jun 19th, 2017]
 *		- Too many to list.  Morphed into full service manager.
 *		- Code credits for TotalConnect pieces go to mhatrey, bdwilson, QCCowboy.  Without these guys, this would have never started
 *		- Reference credit to StrykerSKS (for Ecobee SM) and infofiend (for FLEXi Lighting) where ideas and code segments came from for the SM piece
 *      - Moved from open ended polling times (in seconds) to a list.  This allows us to use better scheduling with predictable options 1 minute and over.
 *
 *  Future Changes Needed
 *      - Add a settings to change credentials in preferences (currently can't get back into credentials page after initial setup unless credentials are failing login)
 *      - Implement Dimmers, Thermostats, & Locks
 *      - Any logic to run like harmony with hubs (automationDevice vs securityDevice) and subdevices?  seems unnecessarily complicated for this, but could provide a device that would give a dashboard view
 *		- Armed Away from Armed Stay or vice versa does not work.  Must disarm first (does not currently handle)
 *		- Change updates from syncronous post calls to async calls (requires rewriting update mechanisms to call, and then handle when response comes)
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

definition(
    name: "TotalConnect 2.0",
    namespace: "Security",
    author: "Jeremy Stroebel",
    description: "Total Connect 2.0 Service Manager",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/yogi/TotalConnect/150.png",
    iconX2Url: "https://s3.amazonaws.com/yogi/TotalConnect/300.png",
    singleInstance: true)

preferences {
	page(name: "credentials", content: "authPage")
	//dynamic page that has duplicate code with deviceSetup (should be a way to remove, maybe with content: and methods?)
    page(name: "deviceSetup", content: "deviceSetup")
	//only runs on first install
    page(name: "sensorSetup", content: "sensorSetup")
	//only runs when sensors are selected, should change to only ask if sensor list has changed?
}

/////////////////////////////////////
// PREFERENCE PAGES
/////////////////////////////////////

def authPage() {
	if (!isTokenValid() && settings.userName != null && settings.password != null) {
		log.debug "Login Token Does not Exist or is likely invalid - Will attempt Login"
       	state.token = login()
    }//Check if there is no login token, but there are login details (this will check to make sure we're logged in before any refresh as well)
    
    if (!isTokenValid())
    {
       	log.debug "Either Login failed or there are no credentials to attempt login yet"
        state.firstRun == true //this essentially reset credentials if login fails, but backend values will stay set (unknown how to resolve that, can't clear settings)
    }//only show credentials if login failed or there are no credentials yet (need a reset credentials option)
	
	if(state.firstRun == null || state.firstRun != false) {
    	//Login Credentials
		dynamicPage(name:"credentials", title:"TotalConnect 2.0 Login", nextPage: "deviceSetup", uninstall: true, install:false) {
        	section ("TotalConnect 2.0 Login Credentials") {
        		paragraph "Give your Total Connect credentials. Recommended to make another user for SmartThings"
    			input("userName", "text", title: "Username", description: "Your username for TotalConnect")
    			input("password", "password", title: "Password", description: "Your Password for TotalConnect", submitOnChange:true)
			}//section
		
        	//Location Selection
        	def hideLocation = false //default to showing location
			def locations
    		def defaultLocation
    		def deviceMap
			def options = []
            
            if(settings.userName != null && settings.password != null) {
	            if(settings.selectedLocation == null) {
	            }//if no location is set, expand this section

	   			locations = locationFound()

    			deviceMap = getDeviceIDs(locations.get(selectedLocation))
				options = locations.keySet() as List ?: []
   				log.debug "Options: " + options

			} else {
	    		hideLocation = true
            }//hide location when there is no username or password
            
        	section("Select from the following Locations for Total Connect.", hideable: true, hidden: hideLocation) {
				input "selectedLocation", "enum", required:true, title:"Select the Location", multiple:false, submitOnChange:true, options:options
       		}//section
            
            //Backend Values (at bottom)
       		section("Backend TotalConnect 2.0 Values - DO NOT CHANGE", hideable: true, hidden: true) {
				paragraph "These are required for login:"
            	input "applicationId", "text", title: "Application ID - It is '14588' currently", description: "Application ID", defaultValue: "14588"
				input "applicationVersion", "text", title: "Application Version - use '3.0.32'", description: "Application Version", defaultValue: "3.0.32"
				paragraph "These are required for device control:"
            	input "locationId", "text", title: "Location ID - Do not change", description: "Location ID", defaultValue: locations?.get(selectedLocation) ?: ""
				input "securityDeviceId", "text", title: "Security Device ID - Do not change", description: "Device ID", defaultValue: deviceMap?.get("Security Panel")
       	    	input "automationDeviceId", "text", required:false, title: "Automation Device ID - Do not change", description: "Device ID", defaultValue: deviceMap?.get("Automation")
			}//section

		}//dynamicPage, Only show this page if missing authentication
	} else {
		deviceSetup()
	}//if this isn't the first run, go straight to device setup
}

private deviceSetup() {
	def nextPage = null //default to no, assuming no sensors
    def install = true //default to true to allow install with no sensors
	if(zoneDevices) {
      	nextPage = "sensorSetup"
		install = false
	}//if we have sensors, make us go to sensorSetup
        
	return dynamicPage(name:"deviceSetup", title:"Pulling up the TotalConnect Device List!",nextPage: nextPage, install: install, uninstall: true) {
		if(zoneDevices) {
			nextPage = "sensorSetup"
			install = false
		} else {
			nextPage = null
			install = true
		} //only set nextPage if sensors are selected (and disable install)
		
        def zoneMap
        def automationMap
		def thermostatMap
        def lockMap
        
		discoverSensors() //have to find zone sensors first
		zoneMap = sensorsDiscovered()

		if(settings.automationDeviceId) {
			log.debug "Automation Discovery Happened"
			discoverSwitches() //have to find switches first
			automationMap = switchesDiscovered()

			discoverThermostats() //have to find thermostats first
			thermostatMap = thermostatsDiscovered()

			discoverLocks() //have to find locks first
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

Map locationFound() {
    log.debug "Executed location function during Setup"

    def locationId
    def locationName
    def locationMap = [:]

	def response = tcCommand("GetSessionDetails", [SessionID: state.token, ApplicationID: applicationId, ApplicationVersion: applicationVersion])
	response.data.Locations.LocationInfoBasic.each { LocationInfoBasic ->
		locationName = LocationInfoBasic.LocationName
		locationId = LocationInfoBasic.LocationID
		locationMap["${locationName}"] = "${locationId}"
	}//LocationInfoBasic.each    							

	log.debug "This is map during Settings " + locationMap
	
    return locationMap
}//locationFound()

Map getDeviceIDs(targetLocationId) {
    log.debug "Executed DeviceID function during Setup"
	if(targetLocationId == null) {
    	log.debug "LocationID not yet defined"
        return [:]
    }
    log.debug "TargetLocationID: ${targetLocationId}"
    def locationId
    String deviceName
    String deviceId
    Map deviceMap = [:]
	
    def response = tcCommand("GetSessionDetails", [SessionID: state.token, ApplicationID: applicationId, ApplicationVersion: applicationVersion])
	response.data.Locations.LocationInfoBasic.each { LocationInfoBasic ->
		locationId = LocationInfoBasic.LocationID
		if(locationId == targetLocationId) {
			LocationInfoBasic.DeviceList.DeviceInfoBasic.each { DeviceInfoBasic ->
				deviceName = DeviceInfoBasic.DeviceName
				deviceId = DeviceInfoBasic.DeviceID
				deviceMap.put(deviceName, deviceId)
			}//iterate throught DeviceIDs
		}//Only get DeviceIDs for the desired location
	}//LocationInfoBasic.each
    
	//log.debug "DeviceID map is " + deviceMap
    
  	return deviceMap
} // Should return Map of Devices associated to the given location

def discoverSensors() {
    def sensors = [:]

    def response = tcCommand("GetPanelMetaDataAndFullStatusEx", [SessionID: state.token, LocationID: settings.locationId, LastSequenceNumber: 0, LastUpdatedTimestampTicks: 0, PartitionID: 1])
    response.data.PanelMetadataAndStatus.Zones.ZoneInfo.each { ZoneInfo ->
		//zoneID = ZoneInfo.'@ZoneID'
		//zoneName = ZoneInfo.'@ZoneDescription'
    	//zoneType //needs to come from input
		sensors[ZoneInfo.'@ZoneID'] = [id: "${ZoneInfo.'@ZoneID'}", name: "${ZoneInfo.'@ZoneDescription'}"]
	}//iterate through zones				

	log.debug "TotalConnect2.0 SM:  ${sensors.size()} sensors found"
    //log.debug sensors

	state.sensors = sensors
} //Should discover sensor information and save to state

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

// Discovers Switch Devices (Switches, Dimmmers, & Garage Doors)
def discoverSwitches() {
    def switches = [:]
	
	def response = tcCommand("GetAllAutomationDeviceStatusEx", [SessionID: state.token, DeviceID: automationDeviceId, AdditionalInput: ''])
   	response.data.AutomationData.AutomationSwitch.SwitchInfo.each { SwitchInfo ->
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

// Discovers Thermostat Devices
def discoverThermostats() {
	def thermostats = [:]

	def response = tcCommand("GetAllAutomationDeviceStatusEx", [SessionID: state.token, DeviceID: automationDeviceId, AdditionalInput: ''])
    response.data.AutomationData.AutomationThermostat.ThermostatInfo.each { ThermostatInfo ->
		//thermostatID = ThermostatInfo.ThermostatID
		//thermostatName = ThermostatInfo.ThermostatName
        thermostats[ThermostatInfo.ThermostatID] = [id: "${ThermostatInfo.ThermostatID}", name: "${ThermostatInfo.ThermostatName}"] //use "${var}" to typecast into String
	}//ThermostatInfo.each    							

	log.debug "TotalConnect2.0 SM:  ${thermostats.size()} thermostats found"
    //log.debug thermostatMap

	state.thermostats = thermostats
} //Should return thermostat information

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
    
// Discovers Lock Devices
def discoverLocks() {
	def locks = [:]

	def response = tcCommand("GetAllAutomationDeviceStatusEx", [SessionID: state.token, DeviceID: automationDeviceId, AdditionalInput: ''])
   	response.data.AutomationData.AutomationLock.LockInfo_Transitional.each { LockInfo_Transitional ->
		//lockID = LockInfo_Transitional.LockID
		//lockName = LockInfo_Transitional.LockName
		locks[LockInfo_Transitional.LockID] = [id: "${LockInfo_Transitional.LockID}", name: "${LockInfo_Transitional.LockName}"] //use "${var}" to typecast into String
	}//iterate through Locks
    
	log.debug "TotalConnect2.0 SM:  ${locks.size()} locks found"
    //log.debug locks

	state.locks = locks
} //Should discover locks information and save to state (could combine all automation to turn 3 calls into 1 or pass XML section for each type to discovery...)

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
    log.debug "Executed login"
	String token
    
    def paramsLogin = [
    	uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/AuthenticateUserLogin",
    	body: [userName: settings.userName , password: settings.password, ApplicationID: settings.applicationId, ApplicationVersion: settings.applicationVersion]
    	]
		httpPost(paramsLogin) { response ->
    	token = response.data.SessionID 
		}

	state.tokenRefresh = now()
	String refreshDate = new Date(state.tokenRefresh).format("EEE MMM d HH:mm:ss Z",  location.timeZone)
	log.debug "Smart Things has logged in at ${refreshDate} SessionID: ${token}" 
    
    return token
} // Returns token      

// Keep Alive Command to keep session alive to reduce login/logout calls.  Keep alive does not confirm it worked so we will use GetSessionDetails instead.
// Currently there is no check to see if this is needed.  Logic for if keepAlive is needed would be state.token != null && now()-state.tokenRefresh < 240000.
// This works on tested assumption token is valid for 4 minutes (240000 milliseconds)
def keepAlive() {
	log.debug "KeepAlive.  State.token: '" + state.token + "'"
    String resultCode
    String resultData
    
    def response = tcCommand("GetSessionDetails", [SessionID: state.token, ApplicationID: settings.applicationId, ApplicationVersion: settings.applicationVersion])
	
    //this check code is redundant
    resultCode = response.data.ResultCode
	
    if(resultCode == "0") {
		String refreshDate = new Date(state.tokenRefresh).format("EEE MMM d HH:mm:ss Z",  location.timeZone)
        log.debug "Session kept alive at ${refreshDate}"
        //tokenRefresh already updated
	} else {
    	log.debug "Session keep alive failed at ${refreshDate}"
    }//doublecheck this worked
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
   		def paramsLogout = [
    			uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/Logout",
    			body: [SessionID: state.token]
    			]
   				httpPost(paramsLogout) { responseLogout ->
        		log.debug "Smart Things has successfully logged out"
        	}
       state.token = null
       state.tokenRefresh = null
}

/////////////////////////////////////
// SmartThings defaults
/////////////////////////////////////

def initialize() {
    state.token = login()
    log.debug "Initialize.  Login produced token: " + state.token
    
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

    pollChildren()

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
                	runEvery5Minute(panelAutoUpdater)
                    break
				case 600:
                	runEvery10Minute(panelAutoUpdater)
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
                	runEvery5Minute(zoneAutoUpdater)
                    break
				case 600:
                	runEvery10Minute(zoneAutoUpdater)
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
                	runEvery5Minute(automationAutoUpdater)
                    break
				case 600:
                	runEvery10Minute(automationAutoUpdater)
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
		state.alarmStatus = alarmPanelStatus()
        updateStatuses()
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
		state.zoneStatus = zoneStatus()
        updateStatuses()
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
		state.switchStatus = automationDeviceStatus()
		updateStatuses()
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

//				d = addChildDevice("jhstroebel", "TotalConnect Lock", dni, null /*Hub ID*/, [name: "Device.${dni}", label: "${newLock?.value.name}", completedSetup: true])
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
	def response = tcCommand("ArmSecuritySystem", [SessionID: state.token, LocationID: settings.locationId, DeviceID: settings.securityDeviceId, ArmType: 0, UserCode: '-1'])
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
    def response = tcCommand("ArmSecuritySystem", [SessionID: state.token, LocationID: settings.locationId, DeviceID: settings.securityDeviceId, ArmType: 3, UserCode: '-1'])
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
    def response = tcCommand("ArmSecuritySystem", [SessionID: state.token, LocationID: settings.locationId, DeviceID: settings.securityDeviceId, ArmType: 1, UserCode: '-1'])
	//we do nothing with response (its almost useless on arm, they want you to poll another command to check for success)

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
    def response = tcCommand("ArmSecuritySystem", [SessionID: state.token, LocationID: settings.locationId, DeviceID: settings.securityDeviceId, ArmType: 2, UserCode: '-1'])
	//we do nothing with response (its almost useless on arm, they want you to poll another command to check for success)

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
    def response = tcCommand("DisarmSecuritySystem", [SessionID: state.token, LocationID: settings.locationId, DeviceID: settings.securityDeviceId, UserCode: '-1'])
	//we do nothing with response

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
	def bypassok
	log.debug "Bypassing Zone: ${zoneId}"

	def response = tcCommand("Bypass", [SessionID: state.token, LocationID: settings.locationId, DeviceID: deviceId, Zone: zoneId, UserCode: '-1'])
    bypassok = response.data
}//bypassSensor

def controlSwitch(childDevice, int switchAction) {		   
	def childDeviceInfo = childDevice.getDeviceNetworkId().split("-") //takes deviceId & switchId from deviceNetworkID in format "TC-DeviceID-SwitchID"
    def deviceId = childDeviceInfo[1]
	def switchId = childDeviceInfo[2]

	def response = tcCommand("ControlASwitch", [SessionID: state.token, DeviceID: deviceId, SwitchID: switchId, SwitchAction: switchAction])
}//controlSwitch

def pollChildren(childDevice = null) {
	//log.debug "pollChildren() - forcePoll: ${state.forcePoll}, lastPoll: ${state.lastPoll}, now: ${now()}"
    
	if(!isTokenValid())
    {
    	log.error "Token is likely expired.  Check Keep alive function in SmartApp"
        state.token = login().toString()
    }//check if token is likely still valid or login.  Might add a sendCommand(command) method and check before sending any commands...
    
    if(childDevice == null) {
        log.debug "pollChildren: No child device passed in, will update all devices"
        //update all devices (after checking that they exist)
        if(settings.alarmDevice) {
        	//update alarm
            state.alarmStatus = alarmPanelStatus()
        }
    	if(settings.zoneDevices) {
        	//update zoneDevices
            state.zoneStatus = zoneStatus()
        }
        if(settings.automationDevices || settings.thermostatDevices || settings.lockDevices) {
        	//update automationDevices
            state.switchStatus = automationDeviceStatus()
        }//automation devices are 1 call... if any exist update all 3 types
    }//check device type and update all of that type only (scheduled polling)
    else {
    	log.debug "pollChildren: childDevice: ${childDevice} passed in"
        def childDeviceInfo = childDevice.getDeviceNetworkId().split("-") //takes deviceId & subDeviceId from deviceNetworkID in format "TC-DeviceID-SubDeviceID"
        def deviceId = childDeviceInfo[1]
                
        if(childDeviceInfo.length == 2) {
        	log.debug "Running Security Panel update only"
            //its a security panel
            state.alarmStatus = alarmPanelStatus()
        } else if(deviceId == settings.securityDeviceId) {
        	log.debug "Running Zone Sensor update(s) only"        	
            //its a zone sensor
            state.zoneStatus = zoneStatus()
        } else if(deviceId == settings.automationDeviceId) {
        	log.debug "Running Automation Device update(s) only"
        	//its an automation device (for now below works, but when thermostats and locks are added, need more definition)
            state.switchStatus = automationDeviceStatus()
        }
        else {
        	log.error "deviceNetworkId is not formatted as expected.  ID: ${childDevice.getDeviceNetworkId()}"
        }
	}//if childDevice is passed in (on demand refresh)
	
    updateStatuses()
    
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

// Gets Panel Metadata. Pulls Zone Data from same call (does not work in testing).  Takes token & location ID as an argument.
def alarmPanelStatus() {
	String alarmCode

/* Variables for zone information (doesn't accurately report status)
	String zoneID
    String zoneStatus
    def zoneMap = [:]
*/
	def response = tcCommand("GetPanelMetaDataAndFullStatusEx", [SessionID: state.token, LocationID: settings.locationId, LastSequenceNumber: 0, LastUpdatedTimestampTicks: 0, PartitionID: 1])
	def data = response.data.children()
	alarmCode = data.Partitions.PartitionInfo.ArmingState

/* Parse zone information into map (doesn't accurately report status)
	zoneMap.put("0", alarmCode) //Put alarm code in as zone 0

	data.Zones.ZoneInfo.each
	{
		ZoneInfo ->
			zoneID = ZoneInfo.'@ZoneID'
			zoneStatus = ZoneInfo.'@ZoneStatus'
			zoneMap.put(zoneID, zoneStatus)
	}

	log.debug "ZoneNumber: ZoneStatus " + zoneMap
    return zoneMap
*/
	state.alarmStatusRefresh = now()
	return alarmCode
} //returns alarmCode

Map zoneStatus() {
    String zoneID
    String zoneStatus
    def zoneMap = [:]
	try {
        //use Ex version to get if zone is bypassable
        def response = tcCommand("GetZonesListInStateEx", [SessionID: state.token, LocationID: settings.locationId, PartitionID: 0, ListIdentifierID: 0])
        def data = response?.data

        data?.ZoneStatus.Zones.ZoneStatusInfoEx.each
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
Map automationDeviceStatus() {
	String switchID
	String switchState
    String switchType
    String switchLevel
    Map automationMap = [:]

	try {
        def response = tcCommand("GetAllAutomationDeviceStatusEx", [SessionID: state.token, DeviceID: settings.automationDeviceId, AdditionalInput: ''])
        response.data.AutomationData.AutomationSwitch.SwitchInfo.each
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
		response.data.AutomationData.AutomationThermostat.ThermostatInfo.each
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
    
	/*		
		response.data.AutomationData.AutomationLock.LockInfo_Transitional.each
        {
            LockInfo_Transitional ->
                automationMap[LockInfo_Transitional.LockID] = [
                    lockID: LockInfo_Transitional.LockID,
                    lockState: LockInfo_Transitional.LockState,
                    batteryState: LockInfo_Transitional.BatteryState]                    ]
        }//LockInfo_Transitional.each
    */
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

def updateStatuses() {
	if(settings.alarmDevice) { 
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
						
					/* These cases don't seem to show up on my panel so I am commenting them out unless someone can prove they are real.  They are not implemented in the Device Handler either
					 * Disarming is instant... not sure it would show up anyway
					case "10307":
						log.debug "Polled Status is: Arming"
						if(d.currentStatus != "Arming") {
							sendEvent(d, [name: "status", value: "Arming", displayed: "true", description: "Refresh: Alarm is Arming"])
                        	//refresh device 3 seconds later?
                        }
						break 
					case "10308":
						log.debug "Polled Status is: Disarming"
						if(d.currentStatus != "Disarming") {
							sendEvent(d, [name: "status", value: "Disarming", displayed: "true", description: "Refresh: Alarm is Disarming"])
							//refresh device 3 seconds later?
						}
						break
					*/
					
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
	}//if(settings.alarmDevice)
    
	def children = getChildDevices()    
	def zoneChildren = children?.findAll { it.deviceNetworkId.startsWith("TC-${settings.securityDeviceId}-") }
	def switchChildren = children?.findAll { it.deviceNetworkId.startsWith("TC-${settings.automationDeviceId}-") }
        
	switchChildren.each { 
		try {
			//log.debug "(Switch) SmartThings State is: " + it.currentStatus
			String switchId = it.getDeviceNetworkId().split("-")[2] //takes switchId from deviceNetworkID in format "TC-DeviceID-SwitchID"
                                
			if(state.switchStatus.containsKey(switchId)) {
				def switchState = state.switchStatus.get(switchId)
				//log.debug "(Switch) Polled State is: ${switchState}"
                    
                switch(switchState) {
                    case "0":
                        //log.debug "Status is: Closed"
                        if(it.currentStatus != "Closed") {
                            sendEvent(it, [name: "status", value: "Closed", displayed: "true", description: "Refresh: Garage Door is Closed", isStateChange: "true"]) }
                        break
                    case "1":
                        //log.debug "Status is: Open"
                        if(it.currentStatus != "Open") {
                            sendEvent(it, [name: "status", value: "Open", displayed: "true", description: "Refresh: Garage Door is Open", isStateChange: "true"]) }
                        break
                    default:
                        log.error "Attempted to update switchState to ${switchState}. Only valid states are 0 or 1."
                        break
                }//switch(switchState)
            }//if(state.switchState.containsKey(switchId)
            else {
                log.error "SwitchId ${switchId} does not exist"
            }
        } catch (e) {
            log.error("Error Occurred Updating Device " + it.displayName + ", Error " + e)
        }// try/catch block
    }//switchChildren.each    

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
}//updateStatuses()

def tcCommand(String path, Map body, Integer retry = 0) {
	def response
    def resultCode
    def resultData
    
	def params = [
		uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/",	
		path: path,
    	body: body
    ]

    try {
    	httpPost(params) { resp ->
        	response = resp
			//response = resp.data //could breakout data, but we're only using .data here and want to change as little code a possible to implement
        }//Post Command
        
		resultCode = response.data.ResultCode
        resultData = response.data.ResultData
		
		if(resultCode == "0") {
            state.tokenRefresh = now() //we ran a successful command, that will keep the token alive
			//String refreshDate = new Date(state.tokenRefresh).format("EEE MMM d HH:mm:ss Z",  location.timeZone)
			//log.debug "Session kept alive at ${refreshDate}"
		} else if(resultCode == "4500") {
        	//this was an arm action which returns this code instead of a status, it sent the command
			state.tokenRefresh = now() //we ran a successful command, that will keep the token alive
        } else if(resultCode == "-102") {
        	//this means the Session ID is invalid, needs to login and try again
            log.error "Command: ${path} failed with ResultCode: ${resultCode} and ResultData: ${resultData}"
            log.debug "Attempting to refresh token and try again"
            state.token = login().toString()
            pause(1000) //pause should allow login to complete before trying again.
			response = tcCommand(path, body)
		} else if(resultCode == "4101") {
        	//this happens sometimes, especially for the zone data... should retry... normally works?
            log.error "Command: ${path} failed with ResultCode: ${resultCode} and ResultData: ${resultData}"
		/* Retry causes goofy issues...		
            if(retry == 0) {
            	pause(2000) //pause 2 seconds (otherwise this hits our rate limit)
           		retry += 1
                response = tcCommand(path, body, retry)
            }//retry after 3 seconds if we haven't retried before
		*/
        } else {
        	//error code we haven't seen or expected, we won't do anything
            log.error "Command: ${path} failed with ResultCode: ${resultCode} and ResultData: ${resultData}"
        }//If result is good, refresh token, if not, try and handle, if its not a code we've seen, throw an error to the log
        
    } catch (SocketTimeoutException e) {
        //identify a timeout and retry?
		log.error "Timeout Error: $e"
	/* Retry causes goofy issues...		
        if(retry == 0) {
        	pause(2000) //pause 2 seconds (otherwise this hits our rate limit)
           	retry += 1
            response = tcCommand(path, body, retry)
		}//retry after 5 seconds if we haven't retried before
	*/
    } catch (e) {
    	log.error "Something unexpected went wrong: $e"
	}//try / catch for httpPost

    return response
}//post command to catch any issues and possibly retry command