metadata {
	//Based on work by Brett Sheleski for Tasomota-Power

	definition(name: "Tasmota Switch", namespace: "davindameron", author: "Davin Dameron", ocfDeviceType: "oic.d.switch", vid:"generic-switch") {
		capability "Polling"
		capability "Refresh"
		capability "Switch"
		capability "Execute"
		capability "Signal Strength"

        command "reload"
        command "updateStatus"
        command "ringpush"
        
        attribute "ringpush", "string"
	}

	// UI tile definitions
	tiles(scale: 2) {

	standardTile("switch", "device.switch", decoration: "flat", width: 3, height: 3, canChangeIcon: true) {
	    state "off", label:'${name}', action: "switch.on", icon: "st.switches.switch.on", backgroundColor:"#ffffff"
	    state "on", label:'${name}', action: "switch.off", icon: "st.switches.switch.off", backgroundColor:"#00a0dc"
	}        
	valueTile("lqi", "device.lqi", decoration: "flat", width: 3, height: 3) {
		state "default", label: 'Signal Strength ${currentValue}%'
	}
	
	standardTile("refresh", "device.switch", width: 3, height: 3, inactiveLabel: false, decoration: "flat") {
			state "default", label:'Refresh', action:"refresh", icon:"st.secondary.refresh"
		}

	main "switch"
		details(["switch", "lqi", "refresh"])
	}

    
	preferences {
		input(name: "ipAddress", type: "string", title: "IP Address", description: "IP Address of Sonoff", displayDuringSetup: true, required: true)
		input(name: "PowerChannel", type: "number", title: "Power Channel (1-8)", description: "Power Channel of the Relay", displayDuringSetup: true, required: true)
		input(name: "PowerChannelLed1", type: "number", title: "LED 1 Channel (1-8)", description: "Power Channel of LED 1", displayDuringSetup: true, required: true)
		input(name: "PowerChannelLed2", type: "number", title: "LED 2 Channel (1-8)", description: "Power Channel of LED 2", displayDuringSetup: true, required: true)
		input(name: "PowerChannelLed3", type: "number", title: "LED 3 Channel (1-8)", description: "Power Channel of LED 3", displayDuringSetup: true, required: true)
		input(name: "turnOnLed1", type: "boolean", title: "Turn on LED 1 Light with Switch?", displayDuringSetup: true, required: false)
		input(name: "turnOnLed2", type: "boolean", title: "Turn on LED 2 Light with Switch?", displayDuringSetup: true, required: false)
		input(name: "turnOnLed3", type: "boolean", title: "Turn on LED 3 Light with Switch?", displayDuringSetup: true, required: false)
		input(name: "useMQTTCommands", type: "boolean", title: "Use MQTT for Commands?", displayDuringSetup: true, required: false)
		input(name: "useMQTT", type: "boolean", title: "Use MQTT for Updates?", displayDuringSetup: true, required: false)
		input(name: "MQTTProxy", type: "string", title: "MQTT Proxy Web Server", description: "MQTT Proxy Web Server", displayDuringSetup: true, required: false)
		input(name: "MQTTTopic", type: "string", title: "MQTT Topic", description: "MQTT Topic", displayDuringSetup: true, required: false)
		input(name: "debugLogging", type: "boolean", title: "Turn on debug logging?", displayDuringSetup:true, required: false)
		input(name: "username", type: "string", title: "Username", description: "Username", displayDuringSetup: false, required: false)
		input(name: "password", type: "password", title: "Password (sent cleartext)", description: "Caution: password is sent cleartext", displayDuringSetup: false, required: false)
		input(name: "useDev", type: "boolean", title: "Use Dev Versions for Upgrade?", displayDuringSetup: true, required: false)
		input(name: "doUpgrade", type: "boolean", title: "Perform Upgrade?", displayDuringSetup: true, required: false)

	}
}

def execute(String command){
	
	if (useMQTT=="true"){
		doLogging "execute($command)";
		if (command) {
			def json = new groovy.json.JsonSlurper().parseText(command);
			if (json) {
				doLogging("execute: Values received: ${json}")
				if (json."StatusSTS"){
					json = json."StatusSTS"
				}
				def on = false

				if (json."Wifi"){
					doLogging("execute: got WIFI")
					def ss = json."Wifi"."RSSI";
					//ss = (ss*255)/100;
					sendEvent(name: "lqi", value: ss);

					def rssi = json."Wifi"."Signal";
					sendEvent(name: "rssi", value: rssi);
				}						
				if (json."POWER${PowerChannel}"!=null) {
					doLogging("execute: got power channel")
					on = json."POWER${PowerChannel}".toString().contains("ON");
					doLogging("execute: setting switch state")
					setSwitchState(on);
				}
				if(PowerChannel==1) {
					if (json."POWER"!=null) {
						doLogging("execute: got power channel")
						on = json."POWER".toString().contains("ON");
						doLogging("execute: setting switch state")
						setSwitchState(on);
					}
				}
			}
			else {
				doLogging("execute: No json received: ${command}")
			}
		}
		else {
			doLogging("execute: No command received")
		}
	}
	
}


def doLogging(value){
	if (debugLogging=="true")
	{
		log.debug value;
	}
}

def installed(){
	doLogging "installed()"
    reload();
}

def updated(){
	doLogging "updated()";
	def sRuleText = "";
	def sPre = "";
	if (turnOnLed1=="true"){
		sRuleText += "${sPre}on%20power${PowerChannel}#state%20do%20power${PowerChannelLed1}%20%25value%25%20endon";
		sPre = "%20";
	}
	if (turnOnLed2=="true"){
		sRuleText += "${sPre}on%20power${PowerChannel}#state%20do%20power${PowerChannelLed2}%20%25value%25%20endon";
		sPre = "%20";
	}
	if (turnOnLed3=="true"){
		sRuleText += "${sPre}on%20power${PowerChannel}#state%20do%20power${PowerChannelLed3}%20%25value%25%20endon";
		sPre = "%20";
	}
	if(sRuleText==""){
		ruleState1(0);
	}
	else{
		ruleDefine1(sRuleText);
		ruleState1(1);
	}
	if (doUpgrade=="true"){
		doLogging "doUpgrade is true"
		setOTAURL()
		doUpgrade()
		device.updateSetting("doUpgrade", false)
		//settings[doUpgrade]="false"
	}
	setOption57(1)
}

def setOption56(value){
	sendCommand("setOption56", value, setOption56Callback);
}

def setOption56Callback(physicalgraph.device.HubResponse response){
	doLogging "setOption56Callback(${response})"
	def jsobj = response?.json;

	doLogging "JSON: ${jsobj}";
}

def setOption57(value){
	sendCommand("setOption57", value, setOption56Callback);
}

def setOption57Callback(physicalgraph.device.HubResponse response){
	doLogging "setOption57Callback(${response})"
	def jsobj = response?.json;

	doLogging "JSON: ${jsobj}";
}


def setOTAURL(){
	if (useDev=="true"){
		sendCommand("OtaUrl", "http://192.168.0.40/tasmota.bin", setOTAURLCallback);
	}
	else {
		sendCommand("OtaUrl", "http://thehackbox.org/tasmota/release/tasmota.bin", setOTAURLCallback);
	}
}

def setOTAURLCallback(physicalgraph.device.HubResponse response){
	doLogging "setOTAURLCallback(${response})"
}

def doUpgrade(){
	sendCommand("Upgrade", "1", doUpgradeCallback)
}

def doUpgradeCallback(physicalgraph.device.HubResponse response){
	doUpgradeCallback "doUpgradeCallback(${response})"
}

def ruleState1(value){
	sendCommand("Rule1", value, ruleState1Callback);
}

def ruleState1Callback(physicalgraph.device.HubResponse response){
	doLogging "ruleState1Callback(${response})"
	def jsobj = response?.json;

	doLogging "JSON: ${jsobj}";
}

def ruleDefine1(value){
	sendCommand("Rule1", value, ruleDefine1Callback);
}

def ruleDefine1Callback(physicalgraph.device.HubResponse response){
	doLogging "ruleDefine1Callback(${response})"
	def jsobj = response?.json;

	doLogging "JSON: ${jsobj}";
}

def reload(){
	doLogging "reload()"
}

def poll() {
	doLogging "POLL"
}

def refresh() {
	doLogging "refresh()"
	sendCommand("Status", "11", refreshCallback)
}


def refreshCallback(physicalgraph.device.HubResponse response){
	doLogging "refreshCallback()"
	def jsobj = response?.json;

	doLogging "JSON: ${jsobj}";
	if (useMQTT!="true"){
		updateStatus(jsobj);
	}

}


def sendCommand(String command, callback) {
    return sendCommand(command, null);
}

def sendCommand(String command, payload, callback) {
	sendHubCommand(createCommand(command, payload, callback))
}

def createCommand(String command, payload, callback){
	if(settings.useMQTTCommands=="true"){
		def dni = null;
		def path="/?topic=cmnd/${settings.MQTTTopic}/${command}&payload=${payload}"
		doLogging(path);

		def params = [
		method: "GET",
		path: path,
		headers: [
		    HOST: "${settings.MQTTProxy}:80"
		]
		]
		doLogging(params);

		def options = [
		callback : callback
		];

		def hubAction = new physicalgraph.device.HubAction(params, dni, options);
	}
	else{

		def ipAddress = ipAddress ?: settings?.ipAddress ?: device.latestValue("ipAddress");
		def username = username ?: settings?.username ?: device.latestValue("username");
		def password = password ?: settings?.password ?: device.latestValue("password");

		doLogging("createCommandAction(${command}:${payload}) to device at ${ipAddress}:80");

		if (!ipAddress) {
			doLogging("aborting. ip address of device not set");
			return null;
		}

		def path = "/cm"
		if (payload){
			path += "?cmnd=${command}%20${payload}"
		}
		else{
			path += "?cmnd=${command}"
		}

		if (username){
			path += "&user=${username}"
			if (password){
				path += "&password=${password}"
			}
		}

		def dni = null;
		doLogging(path);

		def params = [
		method: "GET",
		path: path,
		headers: [
		    HOST: "${ipAddress}:80"
		]
		]

		def options = [
		callback : callback
		];

		def hubAction = new physicalgraph.device.HubAction(params, dni, options);
	}
}

def on(){
    setPower("on")
}

def off(){
    setPower("off")
}

def setPower(power){
	doLogging("Setting power to: $power");

	
	def commandName = "Power${PowerChannel}";
	def payload = power;

	doLogging("COMMAND: $commandName ($payload)");

	def command = createCommand(commandName, payload, "setPowerCallback");;

    	sendHubCommand(command);
}

def setPowerCallback(physicalgraph.device.HubResponse response){
	doLogging "Finished Setting power (channel: ${PowerChannel}), JSON: ${response.json}"
	if (useMQTT!="true"){
		def on = response.json."POWER${PowerChannel}".toString().contains("ON");
		if(PowerChannel==1){
			on = on || response.json."POWER".toString().contains("ON");
		}
		setSwitchState(on);
	}
}

def updateStatus(status){

	if (useMQTT!="true"){
		def on = false
		on = status.StatusSTS."POWER${PowerChannel}" == "ON";
		if(PowerChannel==1){
			on = on || status.StatusSTS."POWER" == "ON";
		}
		setSwitchState(on);
		def json = status.StatusSTS
	
	}
}

def setSwitchState(on){
	doLogging("Setting switch to ${on ? 'ON' : 'OFF'}");

	sendEvent(name: "switch", value: on ? "on" : "off", displayed: true);
}

def ping() {
	doLogging("ping()")
	return refresh()
}
