package co.rsk.tools.processor.examples;
/***************************************************************
 * This Analyzer scans the storage trie and counts accounts,
 * contracts, and other metrics that allow to track the usage
 * of RSK and also  help optimize the unitrie data structure.
 * by SDL
 ****************************************************************/

import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.remasc.RemascTransaction;
import co.rsk.tools.processor.RskBlockProcessor;
import co.rsk.tools.processor.RskProvider;
import co.rsk.trie.NodeReference;
import co.rsk.trie.PathEncoder;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieKeySlice;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.util.ByteUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class StorageAnalyzer  extends RskBlockProcessor {

    RskProvider provider;

    static File file;
    static FileWriter fileWriter;

    public void begin() {
        //createOutputFile();
    }

    public static void createOutputFile() {
        try {
            file = new File("storage.csv");
            if (file.createNewFile()) {
                System.out.println("File created: " + file.getName());
            } else {
                System.out.println("File already exists.");
            }
            fileWriter = new FileWriter(file);
            fileWriter.write("BlockNumber,UnixTime,Accounts,StorageCells,"+
                    "longValues,accountsSize,cellsSize,contracts,storageRoots," +
                    "longValuesSize,sharedLongValuesSize," +
                    "codeSize,sharedCodeSize,embeddedNodes,virtualNodes\n");


        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    public StorageAnalyzer(RskProvider provider) {
        this.provider = provider;
    }

    public enum NodeType {
        AccountMidNode,
        Account,
        Code,
        StorageRoot,
        StorageMidnode,
        StorageCell
    }

    private class ProcessTrieResults {
        int realNodes;
        int embeddedNodes;
        int virtualNodes; // virtualNodes == realNodes + embeddedNodes

        int accounts;
        int storageCells;
        int longValues;
        int nonEmptyCodes;
        int storageRoots;
        long accountsSize;
        long cellsSize;
        long sharedLongValuesSize;
        long longValuesSize;
        long codeSize;
        long sharedCodeSize;
    }
    // use counter
    HashMap<Keccak256,Integer> useCount;


    ////////////////////////////////////////////////////////////////////////////
    // These methods were taken from TrieKeyMapper because they should have been
    // static, and not protected.
    static public byte[] mapRskAddressToKey(RskAddress addr) {
        byte[] secureKey = secureKeyPrefix(addr.getBytes());
        return ByteUtil.merge(TrieKeyMapper.domainPrefix(), secureKey, addr.getBytes());
    }

    static public byte[] secureKeyPrefix(byte[] key) {
        return Arrays.copyOfRange(Keccak256Helper.keccak256(key), 0,TrieKeyMapper.SECURE_KEY_SIZE);
    }
    /////////////////////////////////////////////////////////////////////////////////

    private NodeType processNode(int valueLength,
                                       FastTrieKeySlice childKey,
                                       NodeType previousNodeType,
                                        NodeType nodeType,
                                        ProcessTrieResults results) {
        if (valueLength>0) {
            // the first time, it must be an account
            if (previousNodeType==NodeType.AccountMidNode) {
                // The reference node could be a remasc account ONLY if it has a value (5 bytes)

                boolean couldBeARemasc  = childKey.length() == (1 + TrieKeyMapper.SECURE_KEY_SIZE + RemascTransaction.REMASC_ADDRESS.getBytes().length) * Byte.SIZE;
                if ((couldBeARemasc) && (Arrays.compare(childKey.encode(), remascTrieKey)==0)) {
                        nodeType = NodeType.Account;
                } else
                if (childKey.length()!=248) {
                    System.out.println(childKey.length());
                    System.out.println(ByteUtil.toHexString(childKey.encode()));
                    throw new RuntimeException("Unexpected Node with data");
                }
                // this is an account. We could parse it here.
                nodeType = NodeType.Account;

            } else
            // StorageCells can have children because the storage address is compressed
            // by removing leading zeros.
            if ((previousNodeType==NodeType.StorageRoot) ||
                (previousNodeType == NodeType.StorageMidnode) ||
                    (previousNodeType == NodeType.StorageCell))
            {
                nodeType = NodeType.StorageCell;
                results.storageCells++;
                results.cellsSize +=valueLength;
            } else
                if ((nodeType==NodeType.StorageRoot) || (nodeType==NodeType.Code)) {
                    // NodeType.StorageRoot: The data contained must be a single zero
                    // It's validated later
                } else {
                    // We have anode with data that is a child of another node, but the
                    // parent node shoudn't have children. This is an error.
                    throw new RuntimeException("Invalid node with data");
                }
            // Remasc and standard accounts
            if (nodeType == NodeType.Account) {
                results.accounts++;
                results.accountsSize +=valueLength;

            }
        } else
        if (previousNodeType==NodeType.StorageRoot) {
            nodeType = NodeType.StorageMidnode;
        }
        return nodeType;
    }

    private static byte[] remascTrieKey = mapRskAddressToKey(RemascTransaction.REMASC_ADDRESS);

    private void processReference(NodeReference reference,byte side, FastTrieKeySlice key,
                                         NodeType previousNodeType, ProcessTrieResults results) {
        NodeType nodeType = previousNodeType; // keep for now
        if (!reference.isEmpty()) {
            Optional<Trie> node = reference.getNode();
            // This is an account (but NOt REMASC account, which is special case)
            boolean isStdAccountChild = key.length() == (1 + TrieKeyMapper.SECURE_KEY_SIZE + 20) * Byte.SIZE;


            // By looking at the last byte of the key I can decide if this is the code
            if (isStdAccountChild) {
                // I'm not testing the last 7 bits which I know that are zero
                // Another possibility: key.get(key.length()-8)==1
                if (side==1) {
                    // this is the code node
                    nodeType = NodeType.Code;
                    results.nonEmptyCodes++;
                } else {
                    nodeType = NodeType.StorageRoot;
                    results.storageRoots++;
                }
            }

            TrieKeySlice sharedPath =reference.getNode().get().getSharedPath();

            boolean isRemascAccount = false;
            if (isStdAccountChild) {
                // With the current version of the trie, the shared path should be EXACATLY 7 zero bits
                if ((sharedPath.length()!=7) || (sharedPath.encode()[0]!=0))
                    throw new RuntimeException("Invalid trie");
            }
            //TrieKeySlice childKey = key.rebuildSharedPath(side,sharedPath );
            //TrieKeySlice childKey = keyAppendBit(key,side);
            FastTrieKeySlice childKey = key.appendBit(side);

            if (node.isPresent()) {
                Trie childTrie = node.get();
                processTrie(childTrie, childKey,previousNodeType,nodeType, results,reference.isEmbeddable());
            }
        }
    }
    private static final byte LEFT_CHILD_IMPLICIT_KEY = (byte) 0x00;
    private static final byte RIGHT_CHILD_IMPLICIT_KEY = (byte) 0x01;

    private static TrieKeySlice keyAppend(TrieKeySlice key, TrieKeySlice rest ) {
        if (rest.length()==0)
            return key;

        byte[] newKey = new byte[key.length()+rest.length()];
        for(int i=0;i<key.length();i++) {
            newKey[i] = key.get(i);
        }
        for(int i=0;i<rest.length();i++) {
            newKey[i+key.length()] = rest.get(i);
        }
        // Now I have to encode so that TrieKeySlice decodes ! horrible.
        byte[] encoded =PathEncoder.encode(newKey);
        return TrieKeySlice.fromEncoded(encoded,0,newKey.length, encoded.length);
        // This is private !!!! return new TrieKeySlice(newKey,0,newKey.length);
    }

    private static TrieKeySlice keyAppendBit(TrieKeySlice key, byte side ) {
        byte[] newKey = new byte[key.length()+1];
        for(int i=0;i<key.length();i++) {
            newKey[i] = key.get(i);
        }
        newKey[key.length()] = side;

        // Now I have to encode so that TrieKeySlice decodes ! horrible.
        byte[] encoded =PathEncoder.encode(newKey);
        return TrieKeySlice.fromEncoded(encoded,0,newKey.length, encoded.length);
        // This is private !!!! return new TrieKeySlice(newKey,0,newKey.length);
    }
    long prevTime;
    private void processTrie(Trie trie,FastTrieKeySlice parentKey,
                                    NodeType previousNodeType,
                                    NodeType nodeType,
                                    ProcessTrieResults results,boolean isEmbedded) {

        results.virtualNodes++;
        if (results.virtualNodes%1000==0) {
            System.out.println("Nodes processed: "+results.virtualNodes);
            long currentTime = System.currentTimeMillis();
            long nodesXsec = (results.virtualNodes)*1000/(currentTime-started);
            long nodesXsecNow = (results.virtualNodes-prevNodes)*1000/(currentTime-prevTime);
            System.out.println(" Nodes/sec total: "+nodesXsec);
            System.out.println(" Nodes/sec total: "+nodesXsec+ " nodes/sec now: "+nodesXsecNow);
            prevNodes = results.virtualNodes;
            prevTime = currentTime;
        }
        if (isEmbedded) {
            results.embeddedNodes ++;
        } else
            results.realNodes++;

        // Because TrieKeySlice does not have a append() method, we cannot
        // simply append here the trie shared path into the key (the rebuildSharedPath())
        // method forces a byte prefix. However, what we can do is use rebuildSharedPath()
        // when we are at the root of the tree knowing that the first 8 bits of the key
        // is always 8 zeroes.
        //
        //TrieKeySlice key  = keyAppend(parentKey,trie.getSharedPath());
        FastTrieKeySlice key  = parentKey.append(trie.getSharedPath());
        boolean isAccountByKeyLength = key.length() == (1 + TrieKeyMapper.SECURE_KEY_SIZE + 20) * Byte.SIZE;
        //

        int valueLength =trie.getValueLength().intValue();
        if ((isAccountByKeyLength) && (valueLength==0))
            throw new RuntimeException("Missing account record");

        // Switch to Account node type if that's the case
        nodeType = processNode(valueLength,key,previousNodeType,nodeType,results);

        if (nodeType==NodeType.StorageRoot) {
            // The data contained must be a single zero
            if ((valueLength!=1) || (trie.getValue()[0]!=1))
                throw new RuntimeException("Invalid storage root node");
        }
        if (trie.hasLongValue()) {
            results.longValues++;
            results.longValuesSize +=valueLength;
            if (nodeType==NodeType.Code)
                results.codeSize +=valueLength;

            int previousCounterValue = 0;

            if (useCount.containsKey(trie.getValueHash())) {
                previousCounterValue = useCount.get(trie.getValueHash()).intValue();
                results.sharedLongValuesSize += valueLength;
                if (nodeType==NodeType.Code)
                    results.sharedCodeSize +=valueLength;
            }
            useCount.put(trie.getValueHash(), (previousCounterValue + 1));
        }

        NodeReference leftReference = trie.getLeft();
        processReference(leftReference,LEFT_CHILD_IMPLICIT_KEY,key,nodeType,results);
        NodeReference rightReference = trie.getRight();
        processReference(rightReference,RIGHT_CHILD_IMPLICIT_KEY,key,nodeType,results);
    }

    long started;
    long prevNodes;

    @Override
    public boolean processBlock() {
        useCount = new HashMap<>();
        prevNodes = 0;
        prevTime =0;
        ProcessTrieResults results = new ProcessTrieResults();
        started= System.currentTimeMillis();
        processTrie(getTrieAtCurrentBlock(),FastTrieKeySlice.empty(),
                NodeType.AccountMidNode,NodeType.AccountMidNode,results,false);
        long ended = System. currentTimeMillis();
        String line = ""+currentBlock.getNumber()+","+
                currentBlock.getTimestamp()+","+
                results.accounts+","+
                results.storageCells+","+
                results.longValues+","+
                results.accountsSize+","+
                results.cellsSize+","+
                results.nonEmptyCodes+","+
                results.storageRoots+","+
                results.longValuesSize+","+
                results.sharedLongValuesSize+","+
                results.codeSize+","+
                results.sharedCodeSize+","+
                results.embeddedNodes+","+
                results.virtualNodes
                ;

        System.out.println(line);
        System.out.println("Elapsed time [s]: "+(ended-started)/1000);
        // Now we dump on screeen the usecount of the top 10 contracts
        dumpUsecount();
        try {
            fileWriter.write(line+"\n");
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }
    public void dumpUsecount() {
        // Each keccak hash is one element that represents a whose family of
        // equal contracts.
        TreeMap<Integer,List<Keccak256>> sortedUsecount = new TreeMap<>(Collections.reverseOrder());

        Iterator it = useCount.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Keccak256,Integer> pair = (Map.Entry)it.next();
            if (sortedUsecount.containsKey(pair.getValue())) {
                List<Keccak256> list =     sortedUsecount.get(pair.getValue());
                list.add(pair.getKey());
            } else {
                List<Keccak256> list = new ArrayList<>();
                list.add(pair.getKey());
                sortedUsecount.put(pair.getValue(),list);
            }
        }
        Iterator itSorted = sortedUsecount.entrySet().iterator();
        int index = 0;
        while (itSorted.hasNext() && (index<10)) {
            Map.Entry<Integer,List<Keccak256>> pair = (Map.Entry)itSorted.next();
            List<Keccak256> representatives = pair.getValue();
            String s ="";
            for (int i=0;i<representatives.size();i++) {
                s = s+" "+representatives.get(i).toHexString();
                index++;
                if (index==10) break;
            }
            System.out.println(pair.getKey()+": "+s);
        }
    }

    public void end() {
        //close();
    }

    public static void close() {
        try {
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main (String args[]) {
        int maxBlockchainBlock = 20;//3_210_000; // 3219985
        int minBlock = 100;// maxBlockchainBlock;///1_600_001; //2_400_001;
        int maxBlock = 200;//minBlock+1; // maxBlockchainBlock;
        int step = 20;//800_000;
        RskProvider provider = new RskProvider(args,minBlock,maxBlock,step);
        StorageAnalyzer analyzer = new StorageAnalyzer(provider);
        createOutputFile();
	provider.processBlockchain(analyzer);
	close();
    }
}
