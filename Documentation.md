 ---------------------+-------------------+-----------------------------+------------------------------------
 Automation Types     | Attribute Name    | Commands                    | Attribute Values
 ---------------------+-------------------+-----------------------------+------------------------------------
 switches             | switch            | on, off                     | on, off
 switchLevel          | level             | setLevel(number, number)    | <numeric, percent>
 thermostat           | thermostat        | setHeatingSetpoint,         | temperature, heatingSetpoint
                      |                   | setCoolingSetpoint(number)  | coolingSetpoint, thermostatSetpoint
                      |                   | off, heat, emergencyHeat    | thermostatMode — ["emergency heat", "auto", "cool", "off", "heat"]
                      |                   | cool, setThermostatMode     | thermostatFanMode — ["auto", "on", "circulate"]
                      |                   | fanOn, fanAuto, fanCirculate| thermostatOperatingState — ["cooling", "heating", "pending heat",
                      |                   | setThermostatFanMode, auto  | "fan only", "vent economizer", "pending cool", "idle"]
 temperatureSensors   | temperature       |                             | <numeric, F or C according to unit>
 alarms               | alarm             | strobe, siren, both, off    | strobe, siren, both, off
 valve                | valve             | close, open                 | closed, open
 locks                | lock              | lock, unlock                | locked, unlocked
 ---------------------+-------------------+-----------------------------+------------------------------------

 ---------------------+-------------------+-----------------------------+------------------------------------
 Zone Sensor Types    | Attribute Name    | Commands                    | Attribute Values
 ---------------------+-------------------+-----------------------------+------------------------------------
 motionSensors        | motion            |                             | active, inactive
 contactSensors       | contact           |                             | open, closed
 accelerationSensors  | acceleration      |                             | active, inactive
 shockSensors         | humidity          |                             | active, inactive
 glassbreakSensors    | humidity          |                             | <numeric, percent>
 smokeDetector        | humidity          |                             | <numeric, percent>
 carbonMonoxide       | humidity          |                             | <numeric, percent>
 ---------------------+-------------------+-----------------------------+------------------------------------

General TotalConnect Notes (documenting found and probed values)

Arming State - Alarm Status - Arm Type
10200 - Disarmed
10201 - Armed Away - 0
10202 - Armed Away (Zone Bypassed)
10203 - Armed Stay - 1
10204 - Armed Stay (Zone Bypassed)
10205 - Armed Away (Instant) - 3
10206 - Armed Away (Instant) (Zone Bypassed)
10209 - Armed Stay (Instant) - 2
10210 - Armed Stay (Instant) (Zone Bypassed)
10211 - Disarmed (Zone Bypassed)
10218 - Armed Night Stay - 4
10307 - Arming (Shown from when command is sent until panel actually starts arming countdown)
10308 - Disarming (not shown by TotalConnect on Lynx 5200?)

Zone Types (By Zone Number)
1-44 - Normal
45-47 - Garage Doors (L5200/5210)
45-48 - Garage Doors (L7000)
48-64 - Normal
95 - Fire
96 - Meidcal
99 - Police
140-147 - 4 Button
148-155 - New (Button Zones)??
180-185 - Z-Wave Thermostat Zones (L5200/5210)
180-187 - Z-Wave Thermostat Zones (L7000)

Zone Statuses
0 – Normal
1 – Bypassed
2 – Faulted
8 – Trouble
16 – Tampered
32 – Supervision Failed

Automation SwitchTypes
1 - On/Off
2 - Dimmer
3 - Garage Door?

Automation SwitchStates
0 - Off/Closed
1 - On/Open

Automation SwitchAction
0 - Off
1 - On (Type 1 & 3), 1% On (Type 2)
2-99 - Set SwitchLevel for Type 2

At least a 4.5+ minute token timeout 


Documentation of Settings & State Variables

Settings Variables:

String settings.userName
String settings.password
String settings.selectedLocation
String settings.applicationId
String settings.applicationVersion
String settings.locationId
String settings.securityDeviceId
String settings.automationDeviceId
bool settings.alarmDevice
List? settings.zoneDevices
bool settings.shmIntegration
List? settings.automationDevices
List? settings.thermostatDevices
List? settings.lockDevices
String settings.${Sensor.DeviceID}_zoneType
bool settings.pollOn
Int settings.panelPollingInterval
Int settings.zonePollingInterval
Int settings.automationPollingInterval

State Variables:

String state.token
Long state.tokenRefresh
Map state.sensors
Map state.switches
Map state.thermostats
Map state.locks
String? state.alarmStatus
Map state.zoneStatus
Map state.switchStatus
Device state.alarmPanel
Long state.alarmStatusRefresh 
Long state.zoneStatusRefresh
Long state.automationStatusRefresh
