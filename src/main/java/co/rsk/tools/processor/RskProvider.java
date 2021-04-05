package co.rsk.tools.processor;

import co.rsk.RskContext;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.trie.Trie;
import org.ethereum.core.AccountState;
import org.ethereum.core.Block;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

public class RskProvider {

    public static final String REMASC_BURNED_BALANCE_KEY = "burnedBalance";
    public static final Coin TOTAL_SUPPLY = new Coin(new BigInteger("21000000000000000000000000"));
    public static final BigDecimal WEI = new BigDecimal("1e18");

    private final RskContext ctx;
    private TrieKeyMapper trieKeyMapper;
    private Trie trie;
    private long minBlock;
    private long maxBlock;

    public RskProvider(String rskArgs[]) {
        this.ctx = new RskContext(rskArgs);
        init(ctx.getBlockStore().getMinNumber(), ctx.getBlockStore().getMaxNumber());
    }

    public RskProvider(String rskArgs[], long minBlock, long maxBlock) {
        this.ctx = new RskContext(rskArgs);
        init(minBlock, maxBlock);
    }

    public static BigDecimal toBTC(Coin wei) {
        return new BigDecimal(wei.asBigInteger()).divide(RskProvider.WEI);
    }

    private void init(long minBlock, long maxBlock) {
        this.trieKeyMapper = new TrieKeyMapper();
        this.minBlock = Long.max(minBlock, 1); // skip the genesis block
        this.maxBlock = Long.min(ctx.getBlockStore().getMaxNumber(), maxBlock);
        this.trie = getTrie(this.maxBlock);
    }

    public void processBlockchain(RskBlockProcessor blockProcessor) {
        blockProcessor.setContext(ctx);

        for (long blockNumber = minBlock; blockNumber < maxBlock; blockNumber++) {
            blockProcessor.setState(blockNumber);
            blockProcessor.processBlock();
        }
    }

    public Coin getCirculatingSupply() {
        return TOTAL_SUPPLY.subtract(getBalance(PrecompiledContracts.BRIDGE_ADDR));
    }

    public Coin getBalance(RskAddress addr) {
        byte[] accountData = trie.get(trieKeyMapper.getAccountKey(addr));
        AccountState state = new AccountState(accountData);
        return state.getBalance();
    }

    public byte[] getStoredData(RskAddress addr, String storageKey) {
        return trie.get(trieKeyMapper.getAccountStorageKey(addr, DataWord.fromString(storageKey)));
    }

    private Block getBlock(long blockNumber) {
        return ctx.getBlockStore().getChainBlockByNumber(blockNumber);
    }

    private Trie getTrie(long blockNumber) {
        Block block = getBlock(blockNumber);
        Optional<Trie> otrie = ctx.getTrieStore().retrieve(block.getStateRoot());

        if (otrie.isPresent()) {
            return otrie.get();
        } else {
            throw new RuntimeException("Trie not found for block " + blockNumber);
        }
    }
}
