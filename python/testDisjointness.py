import telnet
import re
import sys
import logging
    
# Assert each edge from the file, with optional removal (if the edge is new)
def assertFile(file, remove):
    # Initialise
    counts = [0,0]
    
    # Find the last edge ID (so anything past is removed)
    lastID = int(telnet.command("lastedge").split("|")[0])
    
    # Read each line, asserting and (optionally) removing
    lines = file.read().splitlines()
    i = 0
    for line in lines:
        edgid = assertEdge(line.split("\t")[0])
    
        # Count the result
        if edgid is not -1:
            counts[0] += 1
            logFile.write("ACCEPTED: " + line + "\n")
        else:
            counts[1] += 1
            logFile.write("REJECTED: " + line + "\n")
    
        # Remove the edge
        if remove and edgid > lastID:
            telnet.command("removeedge " + str(edgid) + " T")
        i += 1
        if (i % 500 == 0):
            print(str(i) + " complete from " + str(file.name))
    print(str(counts[0]) + "/" + str(counts[0] + counts[1]))
    return counts

# A method for evaluating each assertion in a pairwise fashion, keeping counts of disjoint clashes
def pairwiseEval(trueAssertions, falseAssertions, key):
    # For every edge in the true assertions
    index = 0
    # TODO Weed out completely invalid assertions
    trueCounts = [0,0]
    falseCounts = [0,0]
    for a1 in trueAssertions:
        a1ID = assertEdge(a1)
        
        # Name back to normal
        a1 = a1.replace(testnode, key)
        if a1ID == -1:
            logging.debug(a1 + ' is invalid')
            continue
        logging.debug(a1 + ' asserted as ground truth')
        # Check against every other edge
        compareDisjointness(trueAssertions[index + 1:], trueCounts, "TRUE", a1ID, a1, key)
        compareDisjointness(falseAssertions, falseCounts, "FALSE", a1ID, a1, key)
        unassertToID(lastID)
        index += 1
    
    # Print the counts
    print("TRUE: " + str(trueCounts))
    print("FALSE: " + str(falseCounts))
    return [trueCounts, falseCounts]

# Count how many edges are disjoint according to the current state of the ontology
def compareDisjointness(assertions, countArray, type, lastID, groundTruth, key):
    for a in assertions:
        aID = assertEdge(a)
        # Return name to normal for logging
        a = a.replace(testnode, key)
        if aID is not -1:
            countArray[0] += 1
            logging.debug(type + ' ' + a.replace(testnode, key) + ' is not disjoint with ' + groundTruth)
        else:
            logging.debug(type + ' ' + a.replace(testnode, key) + ' is disjoint with ' + groundTruth)
        countArray[1] += 1
        unassertToID(lastID)

# Asserts an edge to the ontology
def assertEdge(edge):
    return int(telnet.command("addedge " + edge + " (\"DisjointTest\")").split("|")[0])

# Unasserts edges from the ontology, though does so by removing any edges past the lastID
def unassertToID(lastID):
    while True:
        nextID = int(telnet.command("nextedge " + str(lastID)).split("|")[0])
        if nextID is -1:
            break
        telnet.command("removeedge " + str(nextID) + " T")

# Groups assertions based on their subject argument
def group(file):
    lines = file.read().splitlines()
    # Test assert each edge
    parsed = []
    for line in lines:
        query = telnet.command("addedge " + line.replace(subject(line), testnode) + " (\"DisjointTest\")")
        parseID = int(query.split('|')[0])
        if parseID != -1:
            parsed.append(line)
            logging.debug('ACCEPTED:' + line)
        else:
            logging.debug('REJECTED:' + line + ' - ' + query)
        # Revert TestNode
        unassertToID(lastID)
    
    paired = [(subject(x), x) for x in parsed]
    keys = set([x[0] for x in paired])
    return dict([(x, [y[1].replace(x, testnode) for y in paired if y[0] == x]) for x in keys])
    
# Return the subject of the edge string
def subject(edgeStr):
    return re.search('\(\S+ (\S+) .+\)', edgeStr).group(1)
    
def pairwiseAssert():
    # For each keypair
    for key in keys:
        # We don't actually need to assert for 'key' - we can use a test node to clear all edges
        print(key + ":")
        trueVals = (trueMap[key] if key in trueMap else [])
        falseVals = (falseMap[key] if key in falseMap else [])
        pairwiseEval(trueVals, falseVals, key)
    
    
    
    
telnet.init(2426, True)
telnet.command('removenode TestNode T')
testnode = telnet.command('addnode TestNode ("DisjointTest")').split('|')[0]

# Logging initialisation
logging.basicConfig(format='%(levelname)s:%(message)s',filename='disjoint.log',level=logging.DEBUG)
with open('disjoint.log', 'w'):
    pass

# Get the files open, and assert each true edge (should stay) and false edge (should not stay)
disjoints = open(sys.argv[1], 'r')
trueFile = open(sys.argv[2], 'r')
falseFile = open(sys.argv[3], 'r')
print("Files read in")

# Organise each assertion into groups by subject
lastID = int(telnet.command("lastedge").split("|")[0])
trueMap = group(trueFile)
print("True assertions grouped")
falseMap = group(falseFile)
print("False assertions grouped")
keys = set(trueMap.keys() + falseMap.keys())
print("Completed grouping")

print("PRE PAIRWISE CHECKING")
lastID = int(telnet.command("lastedge").split("|")[0])
pairwiseAssert()

# Assert the disjoints
print("ASSERTING DISJOINTS")
lines = disjoints.read().splitlines()
i = 0
for line in lines:
    assertEdge(line.split('\t')[0])
    i += 1
    if i % 500 == 0:
        print("Creating disjoints: " + str(i) + " complete")
    
print("POST PAIRWISE CHECKING")
lastID = int(telnet.command("lastedge").split("|")[0])
pairwiseAssert()

telnet.command('removenode ' + testnode + ' T')
    
# OLD STUFF
# Pre counts
# logFile.write("PRE TRUE\n\n")
# print("PRE TRUE")
# preTrue = assertFile(trueFile, True)
# logFile.write("PRE FALSE\n\n")
# print("PRE FALSE")
# preFalse = assertFile(falseFile, True)

# # Assert disjoints
# logFile.write("DISJOINT\n\n")
# print("DISJOINTS")

# # Post counts
# logFile.write("POST TRUE\n\n")
# print("POST TRUE")
# preTrue = assertFile(trueFile, True)
# logFile.write("POST FALSE\n\n")
# print("POST FALSE")
# preFalse = assertFile(falseFile, True)