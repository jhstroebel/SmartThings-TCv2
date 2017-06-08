/**
 *  TotalConnect
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
 *  For complete set of capabilities, attributes, and commands see:
 *
 *  https://graph.api.smartthings.com/ide/doc/capabilities
 *
 *  ---------------------+-------------------+-----------------------------+------------------------------------
 *  Automation Types     | Attribute Name    | Commands                    | Attribute Values
 *  ---------------------+-------------------+-----------------------------+------------------------------------
 *  switches             | switch            | on, off                     | on, off
 *  switchLevel          | level             | setLevel(number, number)    | <numeric, percent>
 *  thermostat           | thermostat        | setHeatingSetpoint,         | temperature, heatingSetpoint
 *                       |                   | setCoolingSetpoint(number)  | coolingSetpoint, thermostatSetpoint
 *                       |                   | off, heat, emergencyHeat    | thermostatMode — ["emergency heat", "auto", "cool", "off", "heat"]
 *                       |                   | cool, setThermostatMode     | thermostatFanMode — ["auto", "on", "circulate"]
 *                       |                   | fanOn, fanAuto, fanCirculate| thermostatOperatingState — ["cooling", "heating", "pending heat",
 *                       |                   | setThermostatFanMode, auto  | "fan only", "vent economizer", "pending cool", "idle"]
 *  temperatureSensors   | temperature       |                             | <numeric, F or C according to unit>
 *  alarms               | alarm             | strobe, siren, both, off    | strobe, siren, both, off
 *  valve                | valve             | close, open                 | closed, open
 *  locks                | lock              | lock, unlock                | locked, unlocked
 *  ---------------------+-------------------+-----------------------------+------------------------------------
 *
 *  ---------------------+-------------------+-----------------------------+------------------------------------
 *  Zone Sensor Types    | Attribute Name    | Commands                    | Attribute Values
 *  ---------------------+-------------------+-----------------------------+------------------------------------
 *  motionSensors        | motion            |                             | active, inactive
 *  contactSensors       | contact           |                             | open, closed
 *  accelerationSensors  | acceleration      |                             | active, inactive
 *  shockSensors         | humidity          |                             | active, inactive
 *  glassbreakSensors    | humidity          |                             | <numeric, percent>
 *  smokeDetector        | humidity          |                             | <numeric, percent>
 *  carbonMonoxide       | humidity          |                             | <numeric, percent>
 *  ---------------------+-------------------+-----------------------------+------------------------------------
 *
 * Alarm Status
 * 10200 - Disarmed
 * 10203 - Armed Stay
 * 10201 - Armed Away
 * 
 * Zone Statuses
 * 0 – Normal
 * 1 – Bypassed
 * 2 – Faulted
 * 8 – Trouble
 * 16 – Tampered
 * 32 – Supervision Failed
 *
 * Automation SwitchTypes
 * 1 - On/Off
 * 2 - Dimmer
 * 3 - Garage Door?
 *
 * At least a 4.5+ minute token timeout 
 */

 /*
 Version: v0.1
 Changes [June 6th, 2017]
	- Started from code by mhatrey @ https://github.com/mhatrey/TotalConnect/blob/master/TotalConnect.groovy 	
	- Modified locationlist to work (as List)
    - Added Autodiscover for Automation and Security Panel DeviceIDs
    - Added Iteration of devices to add (did all automation devices even though I only want a non-zwave garage door)
 	- Removed Success Page (unneccesary)
    
 Future Changes Needed
 	- Modify login to check if successful (doesn't seem to do that now!)
    - Add a settings to change credentials in preferences (currently can't get back into credentials page after initial setup unless credentials are failing login)
    - Automation Device (Zwave module, etc) may not exist.  Current code likely doesn't handle that well
    - Implement thermostats & locks
    - Deal with user removing items in settings (adding is fine)
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
	page(name: "Page 1", content: "authPage")
	//dynamic page that has duplicate code with deviceSetup (should be a way to remove, maybe with content: and methods?)
    page(name: "deviceSetup", content: "deviceSetup")
	//only runs on first install
    page(name: "sensorSetup", content: "sensorSetup")
	//only runs when sensors are selected, should change to only ask if sensor list has changed?

}

// Start of Page Functions
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
		dynamicPage(name:"Page 1", title:"TotalConnect 2.0 Login", nextPage: "deviceSetup", uninstall: true, install:false) {
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
       	    	input "automationDeviceId", "text", title: "Automation Device ID - Do not change", description: "Device ID", defaultValue: deviceMap?.get("Automation")
			}//section

		}//dynamicPage, Only show this page if missing authentication
	} else {
    	//Variables to trigger sensor setup if we have sensors selected
        def nextPage = null //default to no, assuming no sensors
        def install = true //default to true to allow install with no sensors
        if(zoneDevices) {
        	nextPage = "sensorSetup"
           	install = false
        }//if we have sensors, make us go to sensorSetup
        
        dynamicPage(name:"Page 1", title:"Pulling up the TotalConnect Device List!",nextPage: nextPage, install: install, uninstall: true) {
			if(zoneDevices) {
        		nextPage = "sensorSetup"
            	install = false
        	} else {
            	nextPage = null
                install = true
            } //only set nextPage if sensors are selected (and disable install)
            
            //def zoneMap = getSecurityZones()
			//New code to replace above...
			discoverSensors() //have to find zone sensors first
			def zoneMap = sensorsDiscovered()

			//def automationMap = getAutomationDevices()
			//New code to replace above...
			discoverSwitches() //have to find switches first
			def automationMap = switchesDiscovered()
            
            def thermostatMap = getThermostatDevices()
        	def lockMap = getLockDevices()

            def hideAlarmOptions = true
            if(alarmDevice) {
                hideAlarmOptions = false
            }//If alarm is selected, expand options

            section("Select from the following Security devices to add in SmartThings.") {
                input "alarmDevice", "bool", required:true, title:"Honeywell Alarm", defaultValue:false, submitOnChange:true
                input "zoneDevices", "enum", required:false, title:"Select any Zone Sensors", multiple:true, submitOnChange:true, options:zoneMap
            }//section    
            section("Alarm Integration Options:", hideable: true, hidden: hideAlarmOptions) {
                input "shmIntegration", "bool", required: true, title:"Sync alarm status and SHM status", default:false
            }//section
        	section("Select from the following Automation devices to add in SmartThings. (Suggest adding devices directly to SmartThings if compatible)") {
            	input "automationDevices", "enum", required:false, title:"Select any Automation Devices", multiple:true, options:automationMap, hideWhenEmpty:true
            	input "thermostatDevices", "enum", required:false, title:"Select any Thermostat Devices", multiple:true, options:thermostatMap, hideWhenEmpty:true
            	input "lockDevices", "enum", required:false, title:"Select any Lock Devices", multiple:true, options:lockMap, hideWhenEmpty:true
        	}
		}//dynamicPage
	}//if this isn't the first run, go straight to device setup
}

/*
	def description = null
    if (!state.HarmonyAccessToken) {
		if (!state.accessToken) {
			log.debug "Harmony - About to create access token"
			createAccessToken()
		}
        description = "Click to enter Harmony Credentials"
        def redirectUrl = buildRedirectUrl
        return dynamicPage(name: "Credentials", title: "Harmony", nextPage: null, uninstall: true, install:false) {
            section { paragraph title: "Note:", "This device has not been officially tested and certified to “Work with SmartThings”. You can connect it to your SmartThings home but performance may vary and we will not be able to provide support or assistance." }
            section { href url:redirectUrl, style:"embedded", required:true, title:"Harmony", description:description }
        }
    } else {
		//device discovery request every 5 //25 seconds
		int deviceRefreshCount = !state.deviceRefreshCount ? 0 : state.deviceRefreshCount as int
		state.deviceRefreshCount = deviceRefreshCount + 1
		def refreshInterval = 5

		def huboptions = state.HarmonyHubs ?: []
		def actoptions = state.HarmonyActivities ?: []

		def numFoundHub = huboptions.size() ?: 0
    	def numFoundAct = actoptions.size() ?: 0

		if((deviceRefreshCount % 5) == 0) {
			discoverDevices()
		}

		return dynamicPage(name:"Credentials", title:"Discovery Started!", nextPage:"", refreshInterval:refreshInterval, install:true, uninstall: true) {
			section("Please wait while we discover your Harmony Hubs and Activities. Discovery can take five minutes or more, so sit back and relax! Select your device below once discovered.") {
				input "selectedhubs", "enum", required:false, title:"Select Harmony Hubs (${numFoundHub} found)", multiple:true, submitOnChange: true, options:huboptions
			}
      // Virtual activity flag
      if (numFoundHub > 0 && numFoundAct > 0 && true)
			section("You can also add activities as virtual switches for other convenient integrations") {
				input "selectedactivities", "enum", required:false, title:"Select Harmony Activities (${numFoundAct} found)", multiple:true, submitOnChange: true, options:actoptions
			}
    if (state.resethub)
			section("Connection to the hub timed out. Please restart the hub and try again.") {}
		}
    }
}
*/

private deviceSetup() {
	def nextPage = null //default to no, assuming no sensors
    def install = true //default to true to allow install with no sensors
	if(zoneDevices) {
      	nextPage = "sensorSetup"
		install = false
	}//if we have sensors, make us go to sensorSetup
        
	dynamicPage(name:"deviceSetup", title:"Pulling up the TotalConnect Device List!",nextPage: nextPage, install: install, uninstall: true) {
		if(zoneDevices) {
			nextPage = "sensorSetup"
			install = false
		} else {
			nextPage = null
			install = true
		} //only set nextPage if sensors are selected (and disable install)

		discoverSensors() //have to find zone sensors first
		def zoneMap = sensorsDiscovered()
        
		discoverSwitches() //have to find switches first
		def automationMap = switchesDiscovered()
        
        def thermostatMap = getThermostatDevices()
        def lockMap = getLockDevices()
        
        def hideAlarmOptions = true
        if(alarmDevice) {
        	hideAlarmOptions = false
        }//If alarm is selected, expand options
        
    	section("Select from the following Security devices to add in SmartThings.") {
			input "alarmDevice", "bool", required:true, title:"Honeywell Alarm", defaultValue:false, submitOnChange:true
            input "zoneDevices", "enum", required:false, title:"Select any Zone Sensors", multiple:true, submitOnChange:true, options:zoneMap
        }//section    
        section("Alarm Integration Options:", hideable: true, hidden: hideAlarmOptions) {
        	input "shmIntegration", "bool", required: true, title:"Sync alarm status and SHM status", default:false
        }//section
        section("Select from the following Automation devices to add in SmartThings. (Suggest adding devices directly to SmartThings if compatible)") {
            input "automationDevices", "enum", required:false, title:"Select any Automation Devices", multiple:true, options:automationMap, hideWhenEmpty:true
            input "thermostatDevices", "enum", required:false, title:"Select any Thermostat Devices", multiple:true, options:thermostatMap, hideWhenEmpty:true
            input "lockDevices", "enum", required:false, title:"Select any Lock Devices", multiple:true, options:lockMap, hideWhenEmpty:true
        }//section
       
        state.firstRun = false //this page only runs for initial devices setup, after that is done, set firstRun to false to skip Login Preferences
	}//dynamicpage
}//lots of duplicated code from the above authPage()...

private sensorSetup() {
	dynamicPage(name:"sensorSetup", title:"Configure Sensor Types", install: true, uninstall: true) {
        def options = ["contactSensor", "motionSensor"] //sensor options
        
    	section("Select a sensor type for each sensor") {
        	settings.zoneDevices.each { dni ->
                input "${dni}_zoneType", "enum", required:true, title:"${state.sensors.find { ("TC-${settings.securityDeviceId}-${it.value.id}") == dni }?.value.name}", multiple:false, options:options
            }//iterate through selected sensors to get sensor type
		}//section
	}//dynamicPage
    
    //rely on initialization to set sensor types before adding devices
}//sensorSetup()

Map locationFound() {
    log.debug "Executed location function during Setup"

    def locationId
    def locationName
    def locationMap = [:]
    def getSessionParams = [
    	uri: "https://rs.alarmnet.com/tc21api/tc2.asmx/GetSessionDetails",
        body: [ SessionID: state.token, ApplicationID: settings.applicationId, ApplicationVersion: settings.applicationVersion]
    					]
   		httpPost(getSessionParams) { responseSession -> 
        						 responseSession.data.Locations.LocationInfoBasic.each
        						 {
        						 	LocationInfoBasic ->
        						 	locationName = LocationInfoBasic.LocationName
                                    locationId = LocationInfoBasic.LocationID
        						 	locationMap["${locationName}"] = "${locationId}"
        						 }    							
    				}
	log.debug "This is map during Settings " + locationMap
	
    return locationMap
}

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
	def getSessionParams = [
    					uri: "https://rs.alarmnet.com/tc21api/tc2.asmx/GetSessionDetails",
        				body: [ SessionID: state.token, ApplicationID: applicationId, ApplicationVersion: applicationVersion]
    					]
   	httpPost(getSessionParams) { responseSession -> 
        						 responseSession.data.Locations.LocationInfoBasic.each
        						 {
        						 	LocationInfoBasic ->
        						 	locationId = LocationInfoBasic.LocationID
        						 	if(locationId == targetLocationId) {
                                    	LocationInfoBasic.DeviceList.DeviceInfoBasic.each
                                        {
                                            DeviceInfoBasic ->
                                            deviceName = DeviceInfoBasic.DeviceName
                                            deviceId = DeviceInfoBasic.DeviceID
                                            deviceMap.put(deviceName, deviceId)
                                         }//iterate throught DeviceIDs
                                    }//Only get DeviceIDs for the desired location
        						 }    							
    				}
	log.debug "DeviceID map is " + deviceMap
    
  	return deviceMap
} // Should return Map of Devices associated to the given location

def discoverSensors() {
    def sensors = [:]
	
	def getPanelMetaDataAndFullStatusEx = [
		uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/GetPanelMetaDataAndFullStatusEx",
		body: [SessionID: state.token, LocationID: settings.locationId, LastSequenceNumber: 0, LastUpdatedTimestampTicks: 0, PartitionID: 1]
	]
	httpPost(getPanelMetaDataAndFullStatusEx) { responseSession -> 
		responseSession.data.PanelMetadataAndStatus.Zones.ZoneInfo.each { ZoneInfo ->
			//zoneID = ZoneInfo.'@ZoneID'
			//zoneName = ZoneInfo.'@ZoneDescription'
            //zoneType //needs to come from input
			sensors[ZoneInfo.'@ZoneID'] = [id: "${ZoneInfo.'@ZoneID'}", name: "${ZoneInfo.'@ZoneDescription'}"]
		}//iterate through zones				
	}//response captured

	log.debug "TotalConnect2.0 SM:  ${sensors.size()} sensors found"
    log.debug sensors

	state.sensors = sensors
} //Should discover sensor information and save to state (unsure how to define type of sensor yet...)

Map sensorsDiscovered() {
	def sensors =  state.sensors //needs some error checking likely
	def map = [:]

	sensors.each {
			def value = "${it?.value?.name}"
			def key = "TC-${settings.securityDeviceId}-${it?.value?.id}" //Sets DeviceID to "TC-${SecurityID}-${ZoneID}.  Follows format of Harmony activites
			map[key] = value
	}//iterate through discovered sensors to find value
    
    log.debug "Sensors Options: " + map
	return map
}//returns list of sensors for preferences page

def updateSensorTypes() {
    //set sensor type
    settings.zoneDevices.each { dni ->
		log.debug settings["${dni}_zoneType"]
        state.sensors.find { ("TC-${settings.securityDeviceId}-${it.value.id}") == dni }?.value.type = settings["${dni}_zoneType"]
	}//Set type for each sensor after defined
    
    log.debug "Sensors after type: " + state.sensors
}//updates sensors with types from preferences (places in state as fact, probably not a good plan)

// Gets Security Zones (Name & ZoneID) - Depricated by above methods
Map getSecurityZones() {
    String zoneName
    String zoneID
    Map zoneMap = [:]
	
	def getPanelMetaDataAndFullStatusEx = [
		uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/GetPanelMetaDataAndFullStatusEx",
		body: [SessionID: state.token, LocationID: settings.locationId, LastSequenceNumber: 0, LastUpdatedTimestampTicks: 0, PartitionID: 1]
	]
	httpPost(getPanelMetaDataAndFullStatusEx) { responseSession -> 
        						 responseSession.data.PanelMetadataAndStatus.Zones.ZoneInfo.each
        						 {
        						 	ZoneInfo ->
                                        zoneID = ZoneInfo.'@ZoneID'
                                        zoneName = ZoneInfo.'@ZoneDescription'
                                    	zoneMap.put(zoneID, zoneName)
        						 }    							
    				}
	log.debug "ZoneID map is " + zoneMap
    
  	return zoneMap
} //Should return zone information

// Discovers Switch Devices (Switches, Dimmmers, & Garage Doors)
def discoverSwitches() {
    def switches = [:]
	
	def getAllAutomationDeviceStatusEx = [
		uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/GetAllAutomationDeviceStatusEx",
		body: [SessionID: state.token, DeviceID: automationDeviceId, AdditionalInput: '']
	]
	httpPost(getAllAutomationDeviceStatusEx) { responseSession -> 
    	responseSession.data.AutomationData.AutomationSwitch.SwitchInfo.each { SwitchInfo ->
			//switchID = SwitchInfo.SwitchID
			//switchName = SwitchInfo.SwitchName
			//switchType = SwitchInfo.SwitchType
			//switchIcon = SwitchInfo.SwitchIconID // 0-Light, 1-Switch, 255-Garage Door, maybe use for default?
			//switchState = SwitchInfo.SwitchState // 0-Off, 1-On, maybe set initial value?
			//switchLevel = SwitchInfo.SwitchLevel // 0-99, maybe to set intial value?
			switches[SwitchInfo.SwitchID] = [id: "${SwitchInfo.SwitchID}", name: "${SwitchInfo.SwitchName}", type: "${SwitchInfo.SwitchType}"] //use "${var}" to typecast into String
		}//iterate through Switches				
	}//response captured
	log.debug "TotalConnect2.0 SM:  ${switches.size()} switches found"
    log.debug switches

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
    
    log.debug "Switches Options: " + map
	return map
}//returns list of switches for preferences page


// Gets Automation Devices (Name & SwitchID) - Depricated by above methods
Map getAutomationDevices() {
    String switchName
    String switchID
    Map automationMap = [:]
	
	def getAllAutomationDeviceStatusEx = [
		uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/GetAllAutomationDeviceStatusEx",
		body: [SessionID: state.token, DeviceID: automationDeviceId, AdditionalInput: '']
	]
	httpPost(getAllAutomationDeviceStatusEx) { responseSession -> 
        						 responseSession.data.AutomationData.AutomationSwitch.SwitchInfo.each
        						 {
        						 	SwitchInfo ->
                                        switchID = SwitchInfo.SwitchID
                                        switchName = SwitchInfo.SwitchName
                                    	automationMap.put(switchID, switchName)
        						 }    							
    				}
	log.debug "SwitchID map is " + automationMap
    
  	return automationMap
} //Should return automation information

// Gets Thermostat Devices (Name & SwitchID)
Map getThermostatDevices() {
    String thermostatName
    String thermostatID
    Map thermostatMap = [:]
	
	def getAllAutomationDeviceStatusEx = [
		uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/GetAllAutomationDeviceStatusEx",
		body: [SessionID: state.token, DeviceID: automationDeviceId, AdditionalInput: '']
	]
	httpPost(getAllAutomationDeviceStatusEx) { responseSession -> 
        						 responseSession.data.AutomationData.AutomationThermostat.ThermostatInfo.each
        						 {
        						 	ThermostatInfo ->
                                        thermostatID = ThermostatInfo.ThermostatID
                                        thermostatName = ThermostatInfo.ThermostatName
                                    	thermostatMap.put(thermostatID, thermostatName)
        						 }    							
    				}
	log.debug "ThermostatID map is " + thermostatMap

	return thermostatMap
} //Should return thermostat information

// Gets Lock Devices (Name & SwitchID)
Map getLockDevices() {
    String lockName
    String lockID
    Map lockMap = [:]
	
	def getAllAutomationDeviceStatusEx = [
		uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/GetAllAutomationDeviceStatusEx",
		body: [SessionID: state.token, DeviceID: automationDeviceId, AdditionalInput: '']
	]
	httpPost(getAllAutomationDeviceStatusEx) { responseSession -> 
        						 responseSession.data.AutomationData.AutomationLock.LockInfo_Transitional.each
        						 {
        						 	LockInfo_Transitional ->
                                        lockID = LockInfo_Transitional.LockID
                                        lockName = LockInfo_Transitional.LockName
                                    	lockMap.put(lockID, lockName)
        						 }    							
    				}
	log.debug "LockID map is " + lockMap
    
  	return lockMap
} //Should return lock information
// End of Page Functions

// TC2.0 Authentication Methods
// Login Function. Returns SessionID for rest of the functions (doesn't seem to test if login is incorrect...)
def login() {
    log.debug "Executed login"
	String token
    
    def paramsLogin = [
    	uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/AuthenticateUserLogin",
    	body: [userName: settings.userName , password: settings.password, ApplicationID: settings.applicationId, ApplicationVersion: settings.applicationVersion]
    	]
		httpPost(paramsLogin) { responseLogin ->
    	token = responseLogin.data.SessionID 
		}

	state.tokenRefresh = now()
	String refreshDate = new Date(state.tokenRefresh).format("EEE MMM d HH:mm:ss Z",  location.timeZone)
	log.debug "Smart Things has logged in at ${refreshDate} SessionID: ${token}" 
    
    return token
} // Returns token      

// Keep Alive Command to keep session alive to reduce login/logout calls.  Keep alive does not confirm it worked so we will use GetSessionDetails instead.
// Currently there is no check to see if this is needed (we aren't updating tokenRefresh on other HTTP commands, but could to reduce calls to TC service if needed)
// Logic for if keepAlive is needed would be state.token != null && now()-state.tokenRefresh < 240000.  This works on tested assumption token is valid for 4 minutes (240000 milliseconds)
def keepAlive() {
	log.debug "KeepAlive.  State.token: '" + state.token + "'"
    String resultCode
    String resultData
    
	def paramsGetSessionDetails = [
    	uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/GetSessionDetails",
    	body: [SessionID: state.token, ApplicationID: settings.applicationId, ApplicationVersion: settings.applicationVersion]
    	]
		httpPost(paramsGetSessionDetails) { response ->
    		resultCode = response.data.ResultCode
            resultData = response.data.ResultData
//			log.debug "resultCode: '" + resultCode + "'" 
//			log.debug "resultData: '" + resultData + "'"
       	}
		
		if(resultCode == "0") {
            state.tokenRefresh = now()
//			String refreshDate = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(Instant.ofEpochMilli(dateInMs).atZone(ZoneId.of("Europe/London")))
			String refreshDate = new Date(state.tokenRefresh).format("EEE MMM d HH:mm:ss Z",  location.timeZone)
			log.debug "Session kept alive at ${refreshDate}"
		} else {
			log.error "Session keep alive failed: " + resultData
            state.token = login(token).toString()
        }//Re-login if token fails
}

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

// SmartThings defaults
def initialize() {
    state.token = login()
    log.debug "Initialize.  Login produced token: " + state.token
    
    //Send Keep Alive and test token every 3 minutes.  Well inside the tested 4.5+ min expiration
    schedule("0 0/3 * 1/1 * ? *", keepAlive)
	if (settings.alarmDevice && settings.alarmIntegration) {
        subscribe(location, checkMode)
        //subscribe(alarmPanel, checkAlarmMode) //Check for changes to alarm and set SHM
    }//if alarm enabled & smh integration enabled
   	if (automationDevices) {
//		updateSensorTypes() //update sensor types to preference values instead of null defaults before we add devices - maybe a bad idea
    	addDevices()
    }//addDevices if we have any
    
/*    
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
}
def installed() {
	log.debug "Installed with settings: ${settings}"
    initialize()
    }
    
def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
    unschedule()
	initialize()
}

def uninstalled() {
    removeChildDevices(getChildDevices())
}

private removeChildDevices(delete) {
    delete.each {
        deleteChildDevice(it.deviceNetworkId)
    }
}

// ***********
// Handlers
// ***********

// Logic for Triggers based on mode change of SmartThings
def checkMode(evt) {
    	if (evt.value == "Away") {
            	log.debug "Mode is set to Away, Performing ArmAway"
            	//armAway()   
		}//if mode changes to Away
        else if (evt.value == "Night") {
            	log.debug "Mode is set to Night, Performing ArmStay"
            	//armStay()
		}//if mode changes to Night
        else if (evt.value == "Home") {
            	log.debug "Mode is set to Home, Performing Disarm"
            	//disarm()
        }//if mode changes to Home
}//checkMode(evt)



//Child Devices
def addDevices() {
	if(settings.alarmDevice) {
    	
        //Create Alarm Device
    }//if Alarm is selected

	if(settings.zoneDevices) {      
        log.debug "zoneDevices: " + settings.zoneDevices
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
        log.debug "automationDevices: " + settings.automationDevices
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
    
/* Not yet implemented
	if(settings.thermostatDevices) {
		state.thermostats = [:]
        settings.thermostatDevices.each {
            deviceId = it.key
            deviceName = it.value
            state.thermostats.put(deviceId, deviceName) //builds map of thermostat devices
       	}
	}//if thermostat devices are selected
    
    if(settings.lockDevices() {
        state.locks = [:]
        settings.lockDevices.each {
            deviceId = it.key
            deviceName = it.value
            state.locks.put(deviceId, deviceName) //builds map of lock devices
       	}
	}//if lock devices are selected
*/

}//addDevices()

// Alarm Code from Original TC2.0 App that changes with mode
/* Subscribe to changes in mode and change alarm mode
def installed() {
	//log.debug "Installed with settings: ${settings}"
    subscribe(location, checkMode)
}

def updated() {
	//log.debug "Updated with settings: ${settings}"
	unsubscribe()
    subscribe(location, checkMode)
}

// Logic for Triggers based on mode change of SmartThings
def checkMode(evt) {
    	if (evt.value == "Away") {
            	log.debug "Mode is set to Away, Performing ArmAway"
            	armAway()   
            }
        else if (evt.value == "Night") {
            	log.debug "Mode is set to Night, Performing ArmStay"
            	armStay()
            }
        else if (evt.value == "Home") {
            	log.debug "Mode is set to Home, Performing Disarm"
            	disarm()
        }
}

// Gets Panel Metadata. Takes token & location ID as an argument
Map panelMetaData(token, locationId) {
	def alarmCode
    def lastSequenceNumber
    def lastUpdatedTimestampTicks
    def partitionId
 	def getPanelMetaDataAndFullStatus = [
    									uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/GetPanelMetaDataAndFullStatus",
        								body: [ SessionID: token, LocationID: locationId, LastSequenceNumber: 0, LastUpdatedTimestampTicks: 0, PartitionID: 1]
    ]
   	httpPost(getPanelMetaDataAndFullStatus) {	response -> 
        										lastUpdatedTimestampTicks = response.data.PanelMetadataAndStatus.'@LastUpdatedTimestampTicks'
        										lastSequenceNumber = response.data.PanelMetadataAndStatus.'@ConfigurationSequenceNumber'
        										partitionId = response.data.PanelMetadataAndStatus.Partitions.PartitionInfo.PartitionID
        										alarmCode = response.data.PanelMetadataAndStatus.Partitions.PartitionInfo.ArmingState
                                                
    }
	//log.debug "AlarmCode is " + alarmCode
  return [alarmCode: alarmCode, lastSequenceNumber: lastSequenceNumber, lastUpdatedTimestampTicks: lastUpdatedTimestampTicks]
} //Should return alarmCode, lastSequenceNumber & lastUpdateTimestampTicks

// Get LocationID & DeviceID map
Map getSessionDetails(token) {
	def locationId
    def deviceId
    def locationName
    Map locationMap = [:]
    Map deviceMap = [:]
	def getSessionParams = [
    					uri: "https://rs.alarmnet.com/tc21api/tc2.asmx/GetSessionDetails",
        				body: [ SessionID: token, ApplicationID: settings.applicationId, ApplicationVersion: settings.applicationVersion]
    					]
   	httpPost(getSessionParams) { responseSession -> 
        						 responseSession.data.Locations.LocationInfoBasic.each
        						 {
        						 	LocationInfoBasic ->
        						 	locationName = LocationInfoBasic.LocationName
        						 	locationId = LocationInfoBasic.LocationID
        						 	deviceId = LocationInfoBasic.DeviceList.DeviceInfoBasic.DeviceID
        						 	locationMap["${locationName}"] = "${locationId}"
                                    deviceMap["${locationName}"] = "${deviceId}"
        						 }    							
    				}
	// log.debug "Location map is " + locationMap + "& Devie ID map is " + deviceMap
  	return [locationMap: locationMap, deviceMap: deviceMap]
} // Should return Map of Locations

// Arm Function. Performs arming function
def armAway() {        
	def token = login(token)
	def details = getSessionDetails(token) // Gets Map of Location
            // log.debug "This was Selected " + settings.selectedLocation
    	def locationName = settings.selectedLocation
    	def locationId = details.locationMap[locationName]
            // log.debug "ArmAway Function. Location ID is " + locationId
    	def deviceId = details.deviceMap[locationName]
            // log.debug "ArmAway Function. Device ID is " + deviceId
            
    	def paramsArm = [
    			uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/ArmSecuritySystem",
    			body: [SessionID: token, LocationID: locationId, DeviceID: deviceId, ArmType: 0, UserCode: '-1']
    			]
   			httpPost(paramsArm) // Arming Function in away mode
        def metaData = panelMetaData(token, locationId) // Get AlarmCode
            while( metaData.alarmCode != 10201 ){ 
                pause(3000) // 3 Seconds Pause to relieve number of retried on while loop
                metaData = panelMetaData(token, locationId)
            }  
            //log.debug "Home is now Armed successfully" 
            sendPush("Home is now Armed successfully")     
   logout()
}

def armStay() {        
	def token = login(token)
    	def details = getSessionDetails(token) // Gets Map of Location
            // log.debug "This was Selected " + settings.selectedLocation
    	def locationName = settings.selectedLocation
    	def locationId = details.locationMap[locationName]
            // log.debug "ArmStay Function. Location ID is " + locationId
    	def deviceId = details.deviceMap[locationName]
            // log.debug "ArmStay Function. Device ID is " + deviceId
            
    	def paramsArm = [
    			uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/ArmSecuritySystem",
    			body: [SessionID: token, LocationID: locationId, DeviceID: deviceId, ArmType: 1, UserCode: '-1']
    			]
   			httpPost(paramsArm) // Arming function in stay mode
    	def metaData = panelMetaData(token, locationId) // Gets AlarmCode
            while( metaData.alarmCode != 10203 ){ 
                pause(3000) // 3 Seconds Pause to relieve number of retried on while loop
                metaData = panelMetaData(token, locationId)
            } 
            //log.debug "Home is now Armed for Night successfully"     
 			sendPush("Home is armed in Night mode successfully")
    logout()
}

def disarm() {
	def token = login(token)
        def details = getSessionDetails(token) // Get Location & Device ID
            // log.debug "This was Selected " + settings.selectedLocation
        def locationName = settings.selectedLocation
    	def deviceId = details.deviceMap[locationName]
            // log.debug "DisArm Function. Device ID is " + deviceId
    	def locationId = details.locationMap[locationName]
            // log.debug "DisArm Function. Location ID is " + locationId

        	def paramsDisarm = [
    			uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/DisarmSecuritySystem",
    			body: [SessionID: token, LocationID: locationId, DeviceID: deviceId, UserCode: '-1']
    			]
   			httpPost(paramsDisarm)  
   	def metaData = panelMetaData(token, locationId) // Gets AlarmCode
        	while( metaData.alarmCode != 10200 ){ 
                pause(3000) // 3 Seconds Pause to relieve number of retried on while loop
                metaData = panelMetaData(token, locationId)
            }
           // log.debug "Home is now Disarmed successfully"   
           sendPush("Home is now Disarmed successfully")
	logout()         
}
*/