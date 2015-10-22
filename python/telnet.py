import telnetlib

# Basic command reading method
def command(command):
    tn.write(command + "\n")
    res = tn.read_until("\n")
    end = "--message end--"
    return res.strip()[:-len(end) - 1]

# Initialisation method.
def init(portNum, isCyc):
    # Telnet initiation
    global tn
    tn = telnetlib.Telnet("localhost", portNum)
    
    # Set commands
    command("set /env/singleline true")
    command("set /env/prompt  ")
    command("set /env/time F")
    command("set /env/pretty false")
    if isCyc:
        command("set /env/edgeFlags FFT")
        