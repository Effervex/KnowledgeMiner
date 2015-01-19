package knowledgeMiner.debugInterface;

import io.IOManager;

import java.io.IOException;
import java.util.SortedSet;

import knowledgeMiner.ConceptModule;

public class MappingChainInterface extends QuietListInterface {
	private ThreadLocal<StringBuilder> mappingChain_;

	public MappingChainInterface() {
		mappingChain_ = new ThreadLocal<StringBuilder>() {
			@Override
			protected StringBuilder initialValue() {
				try {
					return new StringBuilder();
				} catch (Exception e) {
					e.printStackTrace();
				}
				return null;
			}
		};
	}

	@Override
	public void update(ConceptModule concept,
			SortedSet<ConceptModule> processables) {
		super.update(concept, processables);
		StringBuilder builder = mappingChain_.get();
		switch (concept.getState()) {
		case UNMINED:
			break;
		case UNMAPPED:
			builder.append(" ");
			break;
		case MAPPED:
			builder.append("  ");
			break;
		case REVERSE_MAPPED:
			builder.append("   ");
			break;
		case CONSISTENT:
			builder.append("    ");
			break;
		case ASSERTED:
			builder.append("     ");
			break;
		}
		mappingChain_.get().append(concept.toSimpleString() + "\n");
	}

	@Override
	public void flush() {
		super.flush();
		// Write the thread's data out
		StringBuilder builder = mappingChain_.get();
		try {
			IOManager.getInstance().writeMappingChain(builder.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}
		mappingChain_.set(new StringBuilder());
	}
}
