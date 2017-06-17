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
 *      
 *  Future Changes Needed
 *  	- Modify login to check if successful (doesn't seem to do that now)
 *      - Add a settings to change credentials in preferences (currently can't get back into credentials page after initial setup unless credentials are failing login)
 *      - Automation Device (Zwave module, etc) may not exist.  Current code likely doesn't handle that well
 *      - Implement thermostats & locks
 *      - Deal with user removing items in settings (adding is fine)
 *      - Any logic to run like harmony with hubs (automationDevice vs securityDevice) and subdevices?  seems unnecessarily complicated for this
 *      - Could use post method for all HTTP calls and return XML.  This method could update 
 *		- Need TotalConnect Thermostat and TotalConnect Lock Device Handlers...
 *		- Armed Away from Armed Stay or vice versa does not work.  Must disarm first
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
 * General TotalConnect Notes (documenting found and probed values)
 *
 * Arming State - Alarm Status - Arm Type
 * 10200 - Disarmed
 * 10201 - Armed Away - 0
 * 10202 - Armed Away (Zone Bypassed)
 * 10203 - Armed Stay - 1
 * 10204 - Armed Stay (Zone Bypassed)
 * 10205 - Armed Away (Instant) - 3
 * 10206 - Armed Away (Instant) (Zone Bypassed)
 * 10209 - Armed Stay (Instant) - 2
 * 10210 - Armed Stay (Instant) (Zone Bypassed)
 * 10211 - Disarmed (Zone Bypassed)
 * 10218 - Armed Night Stay - 4
 * 10307 - Arming (Shown from when command is sent until panel actually starts arming countdown)
 * 10308 - Disarming (not shown by TotalConnect on Lynx 5200?)
 *
 * Zone Types (By Zone Number)
 * 1-44 - Normal
 * 45-47 - Garage Doors (L5200/5210)
 * 45-48 - Garage Doors (L7000)
 * 48-64 - Normal
 * 95 - Fire
 * 96 - Meidcal
 * 99 - Police
 * 140-147 - 4 Button
 * 148-155 - New (Button Zones)??
 * 180-185 - Z-Wave Thermostat Zones (L5200/5210)
 * 180-187 - Z-Wave Thermostat Zones (L7000)
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

/* Documentation of Settings & State Variables
 *
 * Settings Variables:
 * 
 * String settings.userName
 * String settings.password
 * String settings.selectedLocation
 * String settings.applicationId
 * String settings.applicationVersion
 * String settings.locationId
 * String settings.securityDeviceId
 * String settings.automationDeviceId
 * bool settings.alarmDevice
 * List? settings.zoneDevices
 * bool settings.shmIntegration
 * List? settings.automationDevices
 * List? settings.thermostatDevices
 * List? settings.lockDevices
 * String settings.${Sensor.DeviceID}_zoneType
 * 
 * State Variables:
 * 
 * String state.token
 * Long state.tokenRefresh
 * Map state.sensors
 * Map state.switches
 * Map state.thermostats
 * Map state.locks
 * Map state.alarmStatus
 * Map state.zoneStatus
 * Map state.switchStatus
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
       	    	input "automationDeviceId", "text", title: "Automation Device ID - Do not change", description: "Device ID", defaultValue: deviceMap?.get("Automation")
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

		discoverSensors() //have to find zone sensors first
		def zoneMap = sensorsDiscovered()
        
		discoverSwitches() //have to find switches first
		def automationMap = switchesDiscovered()
        
        def thermostatMap = getThermostatDevices()

		//def lockMap = getLockDevices()
		discoverLocks() //have to find locks first
		def lockMap = locksDiscovered()
        
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
       
//        state.firstRun = false //this page only runs for initial devices setup, after that is done, set firstRun to false to skip Login Preferences
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
    def getSessionParams = [
    	uri: "https://rs.alarmnet.com/tc21api/tc2.asmx/GetSessionDetails",
        body: [ SessionID: state.token, ApplicationID: settings.applicationId, ApplicationVersion: settings.applicationVersion]
    					]
   		httpPost(getSessionParams) { responseSession -> 
			responseSession.data.Locations.LocationInfoBasic.each { LocationInfoBasic ->
				locationName = LocationInfoBasic.LocationName
				locationId = LocationInfoBasic.LocationID
				locationMap["${locationName}"] = "${locationId}"
			}    							
		}//httpPost
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
        						 responseSession.data.Locations.LocationInfoBasic.each { LocationInfoBasic ->
        						 	locationId = LocationInfoBasic.LocationID
        						 	if(locationId == targetLocationId) {
                                    	LocationInfoBasic.DeviceList.DeviceInfoBasic.each { DeviceInfoBasic ->
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

// Discovers Lock Devices
def discoverLocks() {
	def locks = [:]
	
	def getAllAutomationDeviceStatusEx = [
		uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/GetAllAutomationDeviceStatusEx",
		body: [SessionID: state.token, DeviceID: automationDeviceId, AdditionalInput: '']
	]
	httpPost(getAllAutomationDeviceStatusEx) { responseSession -> 
    	responseSession.data.AutomationData.AutomationLock.LockInfo_Transitional.each { LockInfo_Transitional ->
			//lockID = LockInfo_Transitional.LockID
            //lockName = LockInfo_Transitional.LockName
			locks[LockInfo_Transitional.LockID] = [id: "${LockInfo_Transitional.LockID}", name: "${LockInfo_Transitional.LockName}"] //use "${var}" to typecast into String
		}//iterate through Locks				
	}//response captured
	log.debug "TotalConnect2.0 SM:  ${locks.size()} locks found"
    log.debug locks

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
    
    log.debug "Locks Options: " + map
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

/////////////////////////////////////
// SmartThings defaults
/////////////////////////////////////

def initialize() {
    state.token = login()
    log.debug "Initialize.  Login produced token: " + state.token
    
    //Send Keep Alive and test token every 3 minutes.  Well inside the tested 4.5+ min expiration
    schedule("0 0/3 * 1/1 * ? *", keepAlive)
    
	if (settings.alarmDevice && settings.alarmIntegration) {
        subscribe(location, checkMode)
        //subscribe(alarmPanel, checkAlarmMode) //Check for changes to alarm and set SHM
    }//if alarm enabled & smh integration enabled

/* Combine all selected devices into 1 variable to make sure we have devices and to deleted unused ones
    List state.selectedDevices
    if(alarmDevice) {
    	state.selectedDevices.add("TC-${securityDeviceId}")
    }//if alarmDevice exists
    state.selectedDevices = state.selectedDevices + zoneDevices + automationDevices + thermostatDevices + lockDevices
*/

	if (alarmDevice || zoneDevices || automationDevices || thermostatDevices || lockDevices) {
//		updateSensorTypes() //update sensor types to preference values instead of null defaults before we add devices - maybe a bad idea
    	addDevices()
    }//addDevices if we have any

/* Remove Devices that are no longer selected
    def delete = getChildDevices().findAll { !state.selectedDevices.contains(it.deviceNetworkId) }
	removeChildDevices(delete)
*/

	pollChildren()
}
def installed() {
	log.debug "Installed with settings: ${settings}"
	state.firstRun = false //only run authentication on 1st setup.  After installed, it won't run again

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

/////////////////////////////////////
// HANDLERS
/////////////////////////////////////

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

//No device handler exists yet... commented out addChildDeviceLine until that is resolved...
	if(settings.thermostatDevices) {      
        log.debug "thermostatDevices: " + settings.thermostatDevices
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
        log.debug "lockDevices: " + settings.lockDevices
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
    def paramsArm = [uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/ArmSecuritySystem",
    				body: [SessionID: state.token, LocationID: settings.locationId, DeviceID: settings.securityDeviceId, ArmType: 0, UserCode: '-1']]
	httpPost(paramsArm) // Arming Function in away mode

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

//not used yet...
def armAwayInstant(childDevice) {
    log.debug "TotalConnect2.0 SM: Executing 'armAwayInstant'"
    def paramsArm = [uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/ArmSecuritySystem",
    				body: [SessionID: state.token, LocationID: settings.locationId, DeviceID: settings.securityDeviceId, ArmType: 3, UserCode: '-1']]
	httpPost(paramsArm) // Arming Function in awayInstant mode
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
    def paramsArm = [uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/ArmSecuritySystem",
    				body: [SessionID: state.token, LocationID: settings.locationId, DeviceID: settings.securityDeviceId, ArmType: 1, UserCode: '-1']]
	httpPost(paramsArm) // Arming function in stay mode
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

//not used yet...
def armStayInstant(childDevice) {        
	log.debug "TotalConnect2.0 SM: Executing 'armStayInstant'"
    def paramsArm = [uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/ArmSecuritySystem",
    				body: [SessionID: state.token, LocationID: settings.locationId, DeviceID: settings.securityDeviceId, ArmType: 2, UserCode: '-1']]
	httpPost(paramsArm) // Arming function in stay (instant) mode
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
    def paramsDisarm = [uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/DisarmSecuritySystem",
    				   body: [SessionID: state.token, LocationID: settings.locationId, DeviceID: settings.securityDeviceId, UserCode: '-1']]
	httpPost(paramsDisarm)  
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
	def bypass = [
		uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/Bypass",
		body: [ SessionID: state.token, LocationID: settings.locationId, DeviceID: deviceId, Zone: zoneId, UserCode: '-1']
	]
    log.debug bypass
	httpPost(bypass) {	response -> 
        bypassok = response.data
	}//collects response data into bypassok, could debug if needed
}//bypassSensor

def controlSwitch(childDevice, int switchAction) {		   
	def childDeviceInfo = childDevice.getDeviceNetworkId().split("-") //takes deviceId & switchId from deviceNetworkID in format "TC-DeviceID-SwitchID"
    def deviceId = childDeviceInfo[1]
	def switchId = childDeviceInfo[2]
    
	def paramsControl = [
		uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/ControlASwitch",
		body: [SessionID: state.token, DeviceID: deviceId, SwitchID: switchId, SwitchAction: switchAction]
	]
	httpPost(paramsControl) // Sending Switch Control
}//controlSwitch

def pollChildren(childDevice = null) {
	//log.debug "pollChildren() - forcePoll: ${state.forcePoll}, lastPoll: ${state.lastPoll}, now: ${now()}"
    log.debug childDevice
    
	if(!isTokenValid())
    {
    	log.error "Token is likely expired.  Check Keep alive function in SmartApp"
        state.token = login(token).toString()
    }//check if token is likely still valid or login.  Might add a sendCommand(command) method and check before sending any commands...
    
    if(childDevice == null) {
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
		log.debug childDevice
        def childDeviceInfo = childDevice.getDeviceNetworkId().split("-") //takes deviceId & subDeviceId from deviceNetworkID in format "TC-DeviceID-SubDeviceID"
        def deviceId = childDeviceInfo[1]
                
        if(childDeviceInfo.length == 2) {
        	//its a security panel
            state.alarmStatus = alarmPanelStatus()
        } else if(deviceId == settings.securityDeviceId) {
        	//its a zone sensor
            state.zoneStatus = zoneStatus()
        } else if(deviceId == settings.automationDeviceId) {
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
	
	// Iterate over all the children
	def d = getChildDevices()
    d?.each() { oneChild ->
    	LOG("pollChildren() - Processing poll data for child: ${oneChild} has ${oneChild.capabilities}", 4)
        
    	if( oneChild.hasCapability("Thermostat") ) {
        	// We found a Thermostat, send all of its events
            LOG("pollChildren() - We found a Thermostat!", 5)
            oneChild.generateEvent(atomicState.thermostats[oneChild.device.deviceNetworkId]?.data)
        } else {
        	// We must have a remote sensor
            LOG("pollChildren() - Updating sensor data for ${oneChild}: ${oneChild.device.deviceNetworkId} data: ${atomicState.remoteSensorsData[oneChild.device.deviceNetworkId]?.data}", 4)
            oneChild.generateEvent(atomicState.remoteSensorsData[oneChild.device.deviceNetworkId]?.data)
        } 
    }
    return results
*/// pollChildren from Ecobee Connect Code
}//pollChildren

// Gets Panel Metadata. Pulls Zone Data from same call (does not work in testing).  Takes token & location ID as an argument.
def alarmPanelStatus() {
	//used to return Map
	String alarmCode
/* Variables for zone information (doesn't accurately report status)
	String zoneID
    String zoneStatus
    def zoneMap = [:]
*/

	def getPanelMetaDataAndFullStatusEx = [
		uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/GetPanelMetaDataAndFullStatusEx",
		body: [SessionID: state.token, LocationID: settings.locationId, LastSequenceNumber: 0, LastUpdatedTimestampTicks: 0, PartitionID: 1]
	]
	httpPost(getPanelMetaDataAndFullStatusEx) { responseSession -> 
		def data = responseSession.data.children()

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
*/        
	}
/* Debug and return full zoneMap (doesn't accurately report status)
	log.debug "ZoneNumber: ZoneStatus " + zoneMap
    return zoneMap
*/
	return alarmCode
} //returns alarmCode

Map zoneStatus() {
    String zoneID
    String zoneStatus
    def zoneMap = [:]
	
    //use Ex version to get if zone is bypassable
	def getZonesListInStateEx = [
		uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/GetZonesListInStateEx",
		body: [SessionID: state.token, LocationID: settings.locationId, PartitionID: 0, ListIdentifierID: 0]
	]
	httpPost(getZonesListInStateEx) { responseSession -> 
    	def data = responseSession.data
        
        data.ZoneStatus.Zones.ZoneStatusInfoEx.each
        {
        	ZoneStatusInfoEx ->
            	zoneID = ZoneStatusInfoEx.'@ZoneID'
				zoneStatus = ZoneStatusInfoEx.'@ZoneStatus'
				//bypassable = ZoneStatusInfoEx.'@CanBeBypassed' //0 means no, 1 means yes
				zoneMap.put(zoneID, zoneStatus)
		}//each Zone 
	}//Post response

	log.debug "ZoneNumber: ZoneStatus " + zoneMap
    return zoneMap
} //Should return zone information

// Gets Automation Device Status
Map automationDeviceStatus() {
	String switchID
	String switchState
    Map automationMap = [:]
	
    def getAllAutomationDeviceStatusEx = [
		uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/GetAllAutomationDeviceStatusEx",
		body: [SessionID: state.token, DeviceID: settings.automationDeviceId, AdditionalInput: '']
	]
	httpPost(getAllAutomationDeviceStatusEx) { responseSession ->
        responseSession.data.AutomationData.AutomationSwitch.SwitchInfo.each
        {
            SwitchInfo ->
        		switchID = SwitchInfo.SwitchID
                switchState = SwitchInfo.SwitchState
                automationMap.put(switchID,switchState)
        }
    }
	log.debug "SwitchID: SwitchState " + automationMap

	return automationMap
} //Should return switch state information for all SwitchIDs

def updateStatuses() {
/* Not needed, updates happen in pollChildren()
	def token = state.token
	def locationId = settings.locationId
	def automationDeviceId = settings.automationDeviceId
    def securityDeviceId = settings.securityDeviceId
    
    def securityStatus
    def automationStatus
    
    if(settings.alarmDevice!=null || settings.zoneDevices!=null) {
    	securityStatus = securityDeviceStatus(token, locationId)
    }//Check for alarm device and/or sensor devices before making unnecessary call

	if(settings.deviceList!=null) {
    	automationStatus = automationDeviceStatus(token, automationDeviceID)
    }//Check for automation devices before making unnecessary call
*/
        if(settings.alarmDevice) { 
        	try {
                if(state.alarmStatus) {
					def deviceID = "TC-${settings.securityDeviceId}"
        			def d = getChildDevice(deviceID)
                    
					def currentStatus = state.alarmStatus
                    log.debug "SmartThings Status is: " + d.currentStatus
                    
                    switch(currentStatus) {
                    	case "10200":
                        case "10211": //technically this is Disarmed w/ Zone Bypassed
                        	log.debug "Polled Status is: Disarmed"
                            if(d.currentStatus != "Disarmed") {
                            	sendEvent(d, [name: "status", value: "Disarmed", displayed: "true", description: "Refresh: Alarm is Disarmed"]) }
                            break
                    	case "10201":
                        case "10202": //technically this is Armed Away w/ Zone Bypassed
							log.debug "Polled Status is: Armed Away"
                            if(d.currentStatus != "Armed Away") {
                        		sendEvent(d, [name: "status", value: "Armed Away", displayed: "true", description: "Refresh: Alarm is Armed Away"]) }
                            break
                        case "10203":
                        case "10204": //technically this is Armed Stay w/ Zone Bypassed
							log.debug "Polled Status is: Armed Stay"
                            if(d.currentStatus != "Armed Stay") {
								sendEvent(d, [name: "status", value: "Armed Stay", displayed: "true", description: "Refresh: Alarm is Armed Stay"]) }
                            break
                        case "10205":
                        case "10206": //technically this is Armed Away - Instant w/ Zone Bypassed
							log.debug "Polled Status is: Armed Away - Instant"
                            if(d.currentStatus != "Armed Stay - Instant") {
								sendEvent(d, [name: "status", value: "Armed Away - Instant", displayed: "true", description: "Refresh: Alarm is Armed Away - Instant"]) }
                            break
                        case "10209":
                        case "10210": //technically this is Armed Stay - Instant w/ Zone Bypassed
							log.debug "Polled Status is: Armed Stay - Instant"
                            if(d.currentStatus != "Armed Stay - Instant") {
								sendEvent(d, [name: "status", value: "Armed Stay - Instant", displayed: "true", description: "Refresh: Alarm is Armed Stay - Instant"]) }
                            break                            
                        case "10218":
							log.debug "Polled Status is: Armed Night Stay"
                            if(d.currentStatus != "Armed Night Stay") {
								sendEvent(d, [name: "status", value: "Armed Night Stay", displayed: "true", description: "Refresh: Alarm is Armed Night Stay"]) }
                            break
                        /* These cases don't seem to show up on my panel so I am commenting them out unless someone can prove they are real.  They are not implemented in the Device Handler either
                         * Disarming is instant... not sure it would show up anyway
                        case "10307":
							log.debug "Polled Status is: Arming"
                            if(d.currentStatus != "Arming") {
								sendEvent(d, [name: "status", value: "Arming", displayed: "true", description: "Refresh: Alarm is Arming"]) }
                            break 
                        case "10308":
							log.debug "Polled Status is: Disarming"
                            if(d.currentStatus != "Disarming") {
								sendEvent(d, [name: "status", value: "Disarming", displayed: "true", description: "Refresh: Alarm is Disarming"]) }
                            break
                        */
                        default:
                        	log.debug "Alarm Status returned an irregular value " + currentStatus
                            break
                        }
						sendEvent(name: "refresh", value: "true", displayed: "true", description: "Alarm Refresh Successful") 
				}
				else {
					log.debug "Alarm Code does not exist"
				}
      		} catch (e) {
      			log.error("Error Occurred Updating Alarm "+it.displayName+", Error " + e)
      		}
        }
        def children = getChildDevices()    
        def zoneChildren = children?.findAll { it.deviceNetworkId.startsWith("TC-${settings.securityDeviceId}-") }
        def switchChildren = children?.findAll { it.deviceNetworkId.startsWith("TC-${settings.automationDeviceId}-") }
        
        switchChildren.each { 
        	try {
            	log.debug "(Switch) SmartThings State is: " + it.currentStatus
                String switchId = it.getDeviceNetworkId().split("-")[2] //takes switchId from deviceNetworkID in format "TC-DeviceID-SwitchID"
                
                log.debug "SwitchId is " + switchId
                
                if(state.switchStatus.containsKey(switchId)) {
                	def switchState = state.switchStatus.get(switchId)
                    log.debug "(Switch) Polled State is: ${switchState}"
                    
                    switch(switchState) {
                    	case "0":
							log.debug "Status is: Closed"
							if(it.currentStatus != "Closed") {
	    	                	sendEvent(it, [name: "status", value: "Closed", displayed: "true", description: "Refresh: Garage Door is Closed", isStateChange: "true"]) }
                            break
                    	case "1":
							log.debug "Status is: Open"
							if(it.currentStatus != "Open") {
                           		sendEvent(it, [name: "status", value: "Open", displayed: "true", description: "Refresh: Garage Door is Open", isStateChange: "true"]) }
                            break
                    	default:
    						log.error "Attempted to update switchState to ${switchState}. Only valid states are 0 or 1."
                            break
    				}
				}
				else {
					log.debug "SwitchId ${switchId} does not exist"
				}
      		} catch (e) {
      			log.error("Error Occurred Updating Device " + it.displayName + ", Error " + e)
      		}
        }    
        zoneChildren.each { 
        	try {
                String zoneId = it.getDeviceNetworkId().split("-")[2] //takes zoneId from deviceNetworkID in format "TC-DeviceID-ZoneID"
                String zoneName = it.getDisplayName()
                log.debug "Zone ${zoneId} - ${zoneName}"
                //log.debug "(Sensor) SmartThings State is: " + it.currentContact
                
                if(state.zoneStatus.containsKey(zoneId)) {
                   	String currentStatus = state.zoneStatus.get(zoneId)
                	//log.debug "(Sensor) Polled State is: " + currentStatus
                    def events = []
                    
                    switch(currentStatus) {
                    	case "0":                    
                            log.debug "Zone ${zoneId} is OK"
                            events << [name: "contact", value: "closed"]
                            //sendEvent(it, [name: "status", value: "closed", displayed: "true", description: "Refresh: Zone is closed", isStateChange: "true"])
                            if(it.hasCapability("motionSensor")) {
                                events << [name: "motion", value: "active"]
                                //sendEvent(it, [name: "motion", value: "active", displayed: "true", description: "Refresh: No Motion detected in Zone", isStateChange: "true"])
                            }//if motion sensor, update that as well (maybe do this in the device handler?)
                            break
                    	case "1":                    
                            log.debug "Zone ${zoneId} is Bypassed"
							events << [name: "contact", value: "bypassed"]
                            //sendEvent(it, [name: "contact", value: "bypassed", displayed: "true", description: "Refresh: Zone is bypassed", isStateChange: "true"])
                            break
                    	case "2":                    
                            log.debug "Zone ${zoneId} is Faulted"
							events << [name: "contact", value: "open"]
                            //sendEvent(it, [name: "contact", value: "open", displayed: "true", description: "Refresh: Zone is Faulted", isStateChange: "true"])
                            if(it.hasCapability("motionSensor")) {
                                //sendEvent(it, [name: "motion", value: "active", displayed: "true", description: "Refresh: Motion detected in Zone", isStateChange: "true"])
                            }//if motion sensor, update that as well (maybe do this in the device handler?)
                            break
                    	case "8":                    
                            log.debug "Zone ${zoneId} is Troubled"
							events << [name: "contact", value: "trouble"]
                            //sendEvent(it, [name: "contact", value: "trouble", displayed: "true", description: "Refresh: Zone is Troubled", isStateChange: "true"])
                            break
                    	case "16":                    
                            log.debug "Zone ${zoneId} is Tampered"
							events << [name: "contact", value: "tampered"]
                            //sendEvent(it, [name: "contact", value: "tampered", displayed: "true", description: "Refresh: Zone is Tampered", isStateChange: "true"])
                            break
                    	case "32":                    
                            log.debug "Zone ${zoneId} is Failed"
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

def tcCommand(String path, Map body) {
	def response
	def params = [
		uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/",	
		path: path,
    	body: body
    ]

    try {
    	httpPost(params) { resp ->
        	response = resp.data
        }//Post Command
        
        state.tokenRefresh = now() //we ran a successful command, that will keep the token alive
    } catch (SocketTimeoutException e) {
        //identify a timeout and retry?
		log.error "Timeout Error: $e"
        //response = tcCommand(path, body) //retry command if it fails due to a timeout
    } catch (e) {
    	log.error "Something went wrong: $e"
	}//try / catch for httpPost

    return response
}//post command to catch any issues and possibly retry command
