package co.rsk.tools.processor;

import co.rsk.RskContext;
import co.rsk.trie.Trie;
import org.ethereum.core.Block;
import org.ethereum.db.TrieKeyMapper;

import java.util.Optional;

public abstract class RskBlockProcessor {

    protected RskContext ctx;
    protected Trie trie;
    protected Block currentBlock;
    protected TrieKeyMapper trieKeyMapper = new TrieKeyMapper();

    public abstract void processBlock();

    public void setContext(RskContext ctx) {
        this.ctx = ctx;
    }

    public void setState(long blockNumber) {
        currentBlock = ctx.getBlockStore().getChainBlockByNumber(blockNumber);
        setTrie(blockNumber);
    }

    private void setTrie(long blockNumber) {
        Optional<Trie> otrie = ctx.getTrieStore().retrieve(currentBlock.getStateRoot());

        if (!otrie.isPresent()) {
            throw new RuntimeException("Trie for block " + blockNumber + " not found.");
        }
        trie = otrie.get();
    }
}
