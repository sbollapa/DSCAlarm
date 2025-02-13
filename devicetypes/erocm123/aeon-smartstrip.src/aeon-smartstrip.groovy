/**
 *  Copyright 2017 Eric Maycock
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
metadata {
    definition (name: "Aeon SmartStrip", namespace: "erocm123", author: "Eric Maycock") {
        capability "Switch"
        capability "Energy Meter"
        capability "Power Meter"
        capability "Refresh"
        capability "Configuration"
        capability "Actuator"
        capability "Sensor"
        capability "Temperature Measurement"
        capability "Health Check"
        command "reset"

        (1..6).each { n ->
            attribute "power$n", "number"
            attribute "energy$n", "number"
            command "reset$n"
        }

        fingerprint mfr: "0086", prod: "0003", model: "000B"
        fingerprint deviceId: "0x1001", inClusters: "0x25,0x32,0x27,0x70,0x85,0x72,0x86,0x60", outClusters: "0x82"
    }

    // simulator metadata
    simulator {
        status "on":  "command: 2003, payload: FF"
        status "off":  "command: 2003, payload: 00"
        status "power": new physicalgraph.zwave.Zwave().meterV1.meterReport(
                scaledMeterValue: 30, precision: 3, meterType: 4, scale: 2, size: 4).incomingMessage()
        status "energy": new physicalgraph.zwave.Zwave().meterV1.meterReport(
                scaledMeterValue: 200, precision: 3, meterType: 0, scale: 0, size: 4).incomingMessage()
        status "power1": "command: 600D, payload: 0100" + new physicalgraph.zwave.Zwave().meterV1.meterReport(
                scaledMeterValue: 30, precision: 3, meterType: 4, scale: 2, size: 4).format()
        status "energy2": "command: 600D, payload: 0200" + new physicalgraph.zwave.Zwave().meterV1.meterReport(
                scaledMeterValue: 200, precision: 3, meterType: 0, scale: 0, size: 4).format()
        // reply messages
        reply "2001FF,delay 100,2502": "command: 2503, payload: FF"
        reply "200100,delay 100,2502": "command: 2503, payload: 00"
    }

    preferences {
        input("enableDebugging", "boolean", title:"Enable Debugging", value:false, required:false, displayDuringSetup:false)
    }

    // tile definitions
    tiles(scale: 2) {
        multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "off", label: '${name}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff", nextState:"turningOn"
                attributeState "on", label: '${name}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#00a0dc", nextState:"turningOff"
                attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#00A0DC", nextState:"turningOff"
                attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff", nextState:"turningOn"

            }
            tileAttribute ("statusText", key: "SECONDARY_CONTROL") {
                   attributeState "statusText", label:'${currentValue}'
            }
        }

        childDeviceTiles("outlets")

        valueTile("power", "device.power") {
            state "default", label:'${currentValue} W'
        }
        valueTile("energy", "device.energy") {
            state "default", label:'${currentValue} kWh'
        }
        standardTile("reset", "device.energy", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:'reset kWh', action:"reset"
        }
        standardTile("refresh", "device.power", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        standardTile("configure", "device.configure", inactiveLabel: false, width: 2, height: 2, decoration: "flat") {
            state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
        }
        valueTile("statusText", "statusText", inactiveLabel: false, width: 2, height: 2) {
            state "statusText", label:'${currentValue}', backgroundColor:"#ffffff"
        }
        valueTile("temperature", "device.temperature", inactiveLabel: false, width: 2, height: 2) {
            state "temperature", label:'${currentValue}',
            backgroundColors:
            [
                    [value: 31, color: "#153591"],
                    [value: 44, color: "#1e9cbb"],
                    [value: 59, color: "#90d2a7"],
                    [value: 74, color: "#44b621"],
                    [value: 84, color: "#f1d801"],
                    [value: 95, color: "#d04e00"],
                    [value: 96, color: "#bc2323"]
            ]
        }

        (1..6).each { n ->
            standardTile("outlet$n", "outlet$n", width: 2, height: 2) {
                state "default", label:"outlet$n", icon: "st.switches.switch.off", backgroundColor: "#ffffff"
            }
            valueTile("power$n", "power$n", width: 2, height: 2) {
                state "default", label:'${currentValue} W'
            }
            valueTile("energy$n", "energy$n", width: 2, height: 2) {
                state "default", label:'${currentValue} kWh'
            }
        }

        main(["switch", "outlets"])
        details(["switch", "outlets",
             "outlet1","power1","energy1",
             "outlet2","power2","energy2",
             "outlet3","power3","energy3",
             "outlet4","power4","energy4",
             "outlet5","power5","energy5",
             "outlet6","power6","energy6",
             "temperature", "refresh","reset", "configure"])
    }
}

def parse(String description) {
    def result = []
    if (description.startsWith("Err")) {
        result = createEvent(descriptionText:description, isStateChange:true)
    } else if (description != "updated") {
        def cmd = zwave.parse(description, [0x60: 3, 0x32: 3, 0x25: 1, 0x20: 1])
        //log.debug "Command: ${cmd}"
        if (cmd) {
            result += zwaveEvent(cmd, null)
        }
    }

    def statusTextmsg = ""
    if (device.currentState('power') && device.currentState('energy')) statusTextmsg = "${device.currentState('power').value} W ${device.currentState('energy').value} kWh"
    sendEvent(name:"statusText", value:statusTextmsg, displayed:false)

    //log.debug "parsed '${description}' to ${result.inspect()}"

    result
}

def endpointEvent(endpoint, map) {
    logging("endpointEvent($endpoint, $map)")
    if (endpoint) {
        map.name = map.name + endpoint.toString()
    }
    createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd, ep) {
    def encapsulatedCommand = cmd.encapsulatedCommand([0x32: 3, 0x25: 1, 0x20: 1])
    if (encapsulatedCommand) {
        if (encapsulatedCommand.commandClassId == 0x32) {
            // Metered outlets are numbered differently than switches
            Integer endpoint = cmd.sourceEndPoint
            if (endpoint > 2) {
                zwaveEvent(encapsulatedCommand, endpoint - 2)
            } else if (endpoint == 0) {
                zwaveEvent(encapsulatedCommand, 0)
            } else if (endpoint == 1 || endpoint == 2) {
                zwaveEvent(encapsulatedCommand, endpoint + 4)
            } else {
                log.debug "Ignoring metered outlet ${endpoint} msg: ${encapsulatedCommand}"
                []
            }
        } else {
            zwaveEvent(encapsulatedCommand, cmd.sourceEndPoint as Integer)
        }
    }
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd, endpoint) {
    logging("BasicReport $cmd, $endpoint")
    def cmds = []
    (1..4).each { n ->
            cmds << encap(zwave.switchBinaryV1.switchBinaryGet(), n)
            cmds << "delay 1000"
    }

    return response(cmds)
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd, endpoint) {
    logging("SwitchBinaryReport $cmd, $endpoint")
    zwaveBinaryEvent(cmd, endpoint)
}

def zwaveBinaryEvent(cmd, endpoint) {
    def result = []
    def children = childDevices
    def childDevice = children.find{it.deviceNetworkId.endsWith("$endpoint")}
    if (childDevice) {
        childDevice.sendEvent(name: "switch", value: cmd.value ? "on" : "off")

        if (cmd.value) {
            // One on and the strip is on
            result << createEvent(name: "switch", value: "on")
        } else {
            // All off and the strip is off
            if (!children.any { it.currentValue("switch") == "on" }) {
                result << createEvent(name: "switch", value: "off")
            }
        }
    }
    result
}

def zwaveEvent(physicalgraph.zwave.commands.meterv3.MeterReport cmd, ep) {
    logging("MeterReport")
    def event = [:]
    def cmds = []
    if (cmd.scale < 2) {
        def val = Math.round(cmd.scaledMeterValue*100)/100
        event = endpointEvent(ep, [name: "energy", value: val, unit: ["kWh", "kVAh"][cmd.scale]])
    } else {
        event = endpointEvent(ep, [name: "power", value: (Math.round(cmd.scaledMeterValue * 100)/100), unit: "W"])
    }

    // check if we need to request temperature
    if (!state.lastTempReport || (now() - state.lastTempReport)/60000 >= 5)
    {
        cmds << zwave.configurationV1.configurationGet(parameterNumber: 90).format()
        cmds << "delay 400"
    }

    cmds ? [event, response(cmds)] : event
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd, ep) {
    updateDataValue("MSR", String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId))
    return null
}

def zwaveEvent(physicalgraph.zwave.Command cmd, ep) {
    logging("${device.displayName}: Unhandled ${cmd}" + (ep ? " from endpoint $ep" : ""))
}

def onOffCmd(value, endpoint = null) {
    logging("onOffCmd($value, $endpoint)")
    def actions = [
            new physicalgraph.device.HubAction(encap(zwave.basicV1.basicSet(value: value), endpoint)),
            new physicalgraph.device.HubAction(encap(zwave.switchBinaryV1.switchBinaryGet(), endpoint)),
            new physicalgraph.device.HubAction(encap(zwave.meterV3.meterGet(scale: 2), endpoint)),
    ]
    sendHubCommand(actions, 500)
}



def on() {
    onOffCmd(0xFF)
    refresh()
}
def off() {
    onOffCmd(0x0)
    refresh()
}

def refresh() {
    logging("refresh")
    def cmds = [
        zwave.basicV1.basicGet().format(),
        zwave.meterV3.meterGet(scale: 0).format(),
        zwave.meterV3.meterGet(scale: 2).format(),
        encap(zwave.basicV1.basicGet(), 1)  // further gets are sent from the basic report handler
    ]
            cmds << encap(zwave.switchBinaryV1.switchBinaryGet(), null)
    (1..4).each { endpoint ->
            cmds << encap(zwave.switchBinaryV1.switchBinaryGet(), endpoint)
    }
    (1..6).each { endpoint ->
            cmds << encap(zwave.meterV2.meterGet(scale: 0), endpoint)
            cmds << encap(zwave.meterV2.meterGet(scale: 2), endpoint)
    }
    [90, 101, 102, 111, 112].each { p ->
           cmds << zwave.configurationV1.configurationGet(parameterNumber: p).format()
    }
    delayBetween(cmds, 1000)
}

def ping() {
    logging("ping")
    return zwave.basicV1.basicGet().format()
}

def resetCmd(endpoint = null) {
    logging("resetCmd($endpoint)")
    delayBetween([
        encap(zwave.meterV2.meterReset(), endpoint),
        encap(zwave.meterV2.meterGet(scale: 0), endpoint)
    ])
}

def reset() {
    logging("reset()")
    delayBetween([resetCmd(null), reset1(), reset2(), reset3(), reset4(), reset5(), reset6()])
}

def reset1() { resetCmd(1) }
def reset2() { resetCmd(2) }
def reset3() { resetCmd(3) }
def reset4() { resetCmd(4) }
def reset5() { resetCmd(5) }
def reset6() { resetCmd(6) }

def configure() {
    state.enableDebugging = settings.enableDebugging
    logging("configure()")
    sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
    def cmds = [
        // Configuration of what to include in reports and how often to send them (if the below "change" conditions are met
        // Parameter 101 & 111: Send energy reports every 60 seconds (if conditions are met)
        // Parameter 102 & 112: Send power reports every 15 seconds (if conditions are met)
        zwave.configurationV1.configurationSet(parameterNumber: 101, size: 4, configurationValue: [0,0,0,127]).format(),
        zwave.configurationV1.configurationSet(parameterNumber: 102, size: 4, configurationValue: [0,0,127,0]).format(),
        zwave.configurationV1.configurationSet(parameterNumber: 111, size: 4, scaledConfigurationValue: 60).format(),
        zwave.configurationV1.configurationSet(parameterNumber: 112, size: 4, scaledConfigurationValue: 15).format(),
    ]
    [5, 6, 7, 8, 9, 10, 11].each { p ->
        // Send power reports at the time interval if they have changed by at least 1 watt
        cmds << zwave.configurationV1.configurationSet(parameterNumber: p, size: 2, scaledConfigurationValue: 1).format()
    }
    [12, 13, 14, 15, 16, 17, 18].each { p ->
        // Send energy reports at the time interval if they have changed by at least 5%
        cmds << zwave.configurationV1.configurationSet(parameterNumber: p, size: 1, scaledConfigurationValue: 5).format()
    }
    cmds += [
        // Parameter 4: Induce automatic reports at the time interval if the above conditions are met to reduce network traffic 
        zwave.configurationV1.configurationSet(parameterNumber: 4, size: 1, scaledConfigurationValue: 1).format(),
        // Parameter 80: Enable to send automatic reports to devices in association group 1
        zwave.configurationV1.configurationSet(parameterNumber: 80, size: 1, scaledConfigurationValue: 2).format(),
    ]

    delayBetween(cmds, 1000) + "delay 5000" + refresh()
}

def installed() {
    logging("installed()")
    configure()
    createChildDevices()
}

def updated() {
    logging("updated()")
    configure()

    if (!childDevices) {
        createChildDevices()
    }
    else if (device.label != state.oldLabel) {
        childDevices.each {
            def newLabel = "${device.displayName} (CH${channelNumber(it.deviceNetworkId)})"
            it.setLabel(newLabel)
        }
        state.oldLabel = device.label
    }
}

void childOn(String dni) {
    onOffCmd(0xFF, channelNumber(dni))
}

void childOff(String dni) {
    onOffCmd(0, channelNumber(dni))
}

private channelNumber(String dni) {
    dni.split("-ep")[-1] as Integer
}

private void createChildDevices() {
    state.oldLabel = device.label
    for (i in 1..4) {
        addChildDevice("Aeon SmartStrip Outlet", "${device.deviceNetworkId}-ep${i}", null,
            [completedSetup: true, label: "${device.displayName} (CH${i})",
            isComponent: true, componentName: "ch$i", componentLabel: "Channel $i"])
    }
}

private encap(cmd, endpoint) {
    if (endpoint) {
        if (cmd.commandClassId == 0x32) {
            // Metered outlets are numbered differently than switches
            if (endpoint == 5 || endpoint == 6) {
                endpoint -= 4
            }
            else if (endpoint < 0x80) {
                endpoint += 2
            } else {
                endpoint = ((endpoint & 0x7F) << 2) | 0x80
            }
        }
        zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint:endpoint).encapsulate(cmd).format()
    } else {
        cmd.format()
    }
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv2.ConfigurationReport cmd, ep) {
    def temperatureEvent
    if (cmd.parameterNumber == 90) {
       def temperature = convertTemp(cmd.configurationValue)
       if(getTemperatureScale() == "C"){
          temperatureEvent = [name:"temperature", value: Math.round(temperature * 100) / 100]
       } else {
          temperatureEvent = [name:"temperature", value: Math.round(celsiusToFahrenheit(temperature) * 100) / 100]
       }
       state.lastTempReport = now()
    } else {
       //log.debug "${device.displayName} parameter '${cmd.parameterNumber}' with a byte size of '${cmd.size}' is set to '${cmd.configurationValue}'"
    }
    if (temperatureEvent) {
       createEvent(temperatureEvent)
    }
}

def convertTemp(value) {
   def highbit = value[0]
   def lowbit = value[1]

   if (highbit > 127) highbit = highbit - 128 - 128
   lowbit = lowbit * 0.00390625

   return highbit+lowbit
}

private def logging(message) {
    if (state.enableDebugging == "true") log.debug message
}