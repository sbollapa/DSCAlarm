/*
 *  DSC Zone Flood Device
 *
 *  Author: Jordan <jordan@xeron.cc>
 *  Originally By: Matt Martz <matt.martz@gmail.com>, Kent Holloway <drizit@gmail.com>
 *  Date: 2018-08-29
 */

// for the UI
metadata {
  definition (name: "DSC Zone Flood", author: "jordan@xeron.cc", namespace: 'dsc') {
    // Change or define capabilities here as needed
    capability "Water Sensor"
    capability "Sensor"
    capability "Alarm"
    capability "Momentary"
    attribute "bypass", "string"
    attribute "trouble", "string"

    // Add commands as needed
    command "zone"
    command "bypass"
  }

  simulator {
    // Nothing here, you could put some testing stuff here if you like
  }

  tiles(scale: 2) {
    multiAttributeTile(name:"zone", type: "generic", width: 6, height: 4) {
      tileAttribute ("device.water", key: "PRIMARY_CONTROL") {
        attributeState "wet", label:'wet', icon:"st.alarm.water.wet", backgroundColor:"#53a7c0"
        attributeState "dry", label:'dry', icon:"st.alarm.water.dry", backgroundColor:"#ffffff"
        attributeState "alarm", label:'ALARM', icon:"st.alarm.water.wet", backgroundColor:"#ff0000"
      }
      tileAttribute ("device.trouble", key: "SECONDARY_CONTROL") {
        attributeState "restore", label: 'No Trouble', icon: "st.security.alarm.clear"
        attributeState "tamper", label: 'Tamper', icon: "st.security.alarm.alarm"
        attributeState "fault", label: 'Fault', icon: "st.security.alarm.alarm"
      }
    }
    standardTile("bypass", "device.bypass", width: 3, height: 2, title: "Bypass Status", decoration:"flat"){
      state "off", label: 'Enabled', action: "bypass", icon: "st.security.alarm.on"
      state "on", label: 'Bypassed', action: "bypass", icon: "st.security.alarm.off"
    }
    standardTile("alarm", "device.alarm", width: 3, height: 2, title: "Alarm Status", decoration: "flat"){
      state "off", label: 'No Alarm', icon: "st.security.alarm.off"
      state "both", label: 'ALARM', icon: "st.security.alarm.on"
    }

    // This tile will be the tile that is displayed on the Hub page.
    main "zone"

    // These tiles will be displayed when clicked on the device, in the order listed here.
    details(["zone", "bypass", "alarm"])
  }
}

// handle commands
def bypass() {
  def zone = device.deviceNetworkId.minus('dsczone')
  parent.sendUrl("bypass?zone=${zone}")
}

def push() {
  bypass()
}

def zone(String state) {
  // state will be a valid state for a zone (open, closed)
  // zone will be a number for the zone
  log.debug "Zone: ${state}"

  def troubleList = ['fault','tamper','restore']
  def bypassList = ['on','off']
  def alarmMap = [
    'alarm': "both",
    'noalarm': "off"
  ]

  if (troubleList.contains(state)) {
    sendEvent (name: "trouble", value: "${state}")
  } else if (bypassList.contains(state)) {
    sendEvent (name: "bypass", value: "${state}")
  } else {
    // Send actual alarm state, if we have one
    if (alarmMap.containsKey(state)) {
      sendEvent (name: "alarm", value: "${alarmMap[state]}")
    }
    // Since this is a water sensor device we need to convert the values to match the device capabilities
    // Alarming isn't a valid option for this capability, but we map this here anyway, so you can more easily tell which device
    // is alarming from the "things" page.
    def eventMap = [
     'open':"wet",
     'closed':"dry",
     'noalarm':"dry",
     'alarm':"alarm"
    ]

    // Send final event
    sendEvent (name: "water", value: "${eventMap[state]}")
  }
}