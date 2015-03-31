/*
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package weka.filters.unsupervised.instance;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Vector;

import weka.core.Capabilities;
import weka.core.Capabilities.Capability;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Option;
import weka.core.OptionHandler;
import weka.core.Range;
import weka.core.RevisionUtils;
import weka.core.Utils;
import weka.filters.SimpleBatchFilter;

/**
 * <!-- globalinfo-start --> Removes custom duplicate instances from the first
 * batch of data it receives based on a specific duplicate criteria.
 * <p/>
 * <!-- globalinfo-end -->
 * 
 * <!-- options-start --> Valid options are:
 * <p/>
 * 
 * <pre>
 * -I &lt;col&gt;
 *  Defines the attributes to compare duplicates on.
 * </pre>
 * 
 * <!-- options-end -->
 *
 * @author Sam Sarjant
 * @version $Revision: 1$
 */
public class RemoveCustomDuplicates extends SimpleBatchFilter implements
		OptionHandler {

	/** for serialization. */
	private static final long serialVersionUID = 4518686110979589602L;

	protected Range m_SelectCols = new Range();

	@Override
	public String[] getOptions() {
		Vector<String> options = new Vector<String>();

		if (!getAttributeIndices().equals("")) {
			options.add("-I");
			options.add(getAttributeIndices());
		}

		return options.toArray(new String[0]);
	}

	/**
	 * Parses a given list of options.
	 * <p/>
	 * 
	 * <!-- options-start --> Valid options are:
	 * <p/>
	 * 
	 * <pre>
	 * -I &lt;col&gt;
	 *  Defines the attributes to compare duplicates on. (default all)
	 * </pre>
	 * 
	 * <!-- options-end -->
	 * 
	 * @param options
	 *            the list of options as an array of strings
	 * @throws Exception
	 *             if an option is not supported
	 */
	@Override
	public void setOptions(String[] options) throws Exception {
		String deleteList = Utils.getOption('I', options);
		if (deleteList.length() != 0) {
			setAttributeIndices(deleteList);
		}

		Utils.checkForRemainingOptions(options);
	}

	public void setAttributeIndices(String duplicateList) {
		m_SelectCols.setRanges(duplicateList);
	}

	/**
	 * Get the current range selection.
	 * 
	 * @return a string containing a comma separated list of ranges
	 */
	public String getAttributeIndices() {
		return m_SelectCols.getRanges();
	}

	/**
	 * Returns the tip text for this property
	 * 
	 * @return tip text for this property suitable for displaying in the
	 *         explorer/experimenter gui
	 */
	public String attributeIndicesTipText() {

		return "Specify range of attributes to act on."
				+ " This is a comma separated list of attribute indices, with"
				+ " \"first\" and \"last\" valid values. Specify an inclusive"
				+ " range with \"-\". E.g: \"first-3,5,6-10,last\".";
	}

	@Override
	public Enumeration<Option> listOptions() {
		Vector<Option> options = new Vector<>(1);
		options.addElement(new Option(
				"\tSpecify comparison indices for duplication. Default all.",
				"I", 1, "-I <index1,index2-4,...>"));
		return options.elements();
	}

	/**
	 * Returns a string describing this filter.
	 *
	 * @return a description of the filter suitable for displaying in the
	 *         explorer/experimenter gui
	 */
	@Override
	public String globalInfo() {
		return "Removes all duplicate instances according to a custom criteria from the first batch of data it receives.";
	}

	/**
	 * Input an instance for filtering. Filter requires all training instances
	 * be read before producing output (calling the method batchFinished() makes
	 * the data available). If this instance is part of a new batch, m_NewBatch
	 * is set to false.
	 *
	 * @param instance
	 *            the input instance
	 * @return true if the filtered instance may now be collected with output().
	 * @throws IllegalStateException
	 *             if no input structure has been defined
	 * @throws Exception
	 *             if something goes wrong
	 * @see #batchFinished()
	 */
	@Override
	public boolean input(Instance instance) throws Exception {
		if (getInputFormat() == null)
			throw new IllegalStateException("No input instance format defined");

		if (m_NewBatch) {
			resetQueue();
			m_NewBatch = false;
		}

		if (isFirstBatchDone()) {
			push(instance);
			return true;
		} else {
			bufferInput(instance);
			return false;
		}
	}

	/**
	 * Returns the Capabilities of this filter.
	 *
	 * @return the capabilities of this object
	 * @see Capabilities
	 */
	@Override
	public Capabilities getCapabilities() {
		Capabilities result = super.getCapabilities();
		result.disableAll();

		// attributes
		result.enable(Capability.STRING_ATTRIBUTES);
		result.enable(Capability.NOMINAL_ATTRIBUTES);
		result.enable(Capability.NUMERIC_ATTRIBUTES);
		result.enable(Capability.DATE_ATTRIBUTES);
		result.enable(Capability.MISSING_VALUES);

		// class
		result.enable(Capability.STRING_CLASS);
		result.enable(Capability.NOMINAL_CLASS);
		result.enable(Capability.NUMERIC_CLASS);
		result.enable(Capability.DATE_CLASS);
		result.enable(Capability.MISSING_CLASS_VALUES);
		result.enable(Capability.NO_CLASS);

		return result;
	}

	/**
	 * Determines the output format based on the input format and returns this.
	 *
	 * @param inputFormat
	 *            the input format to base the output format on
	 * @return the output format
	 * @throws Exception
	 *             in case the determination goes wrong
	 */
	@Override
	protected Instances determineOutputFormat(Instances inputFormat)
			throws Exception {

		return new Instances(inputFormat, 0);
	}

	/**
	 * returns true if the output format is immediately available after the
	 * input format has been set and not only after all the data has been seen
	 * (see batchFinished())
	 *
	 * @return true if the output format is immediately available
	 * @see #batchFinished()
	 * @see #setInputFormat(Instances)
	 */
	protected boolean hasImmediateOutputFormat() {

		return true;
	}

	/**
	 * Processes the given data (may change the provided dataset) and returns
	 * the modified version. This method is called in batchFinished().
	 *
	 * @param instances
	 *            the data to process
	 * @return the modified data
	 * @throws Exception
	 *             in case the processing goes wrong
	 * @see #batchFinished()
	 */
	@Override
	protected Instances process(Instances instances) throws Exception {

		if (!isFirstBatchDone()) {
			SortedSet<Instance> seenInstances = new TreeSet<>(
					new DuplicateComparator());
			Instances newInstances = new Instances(instances,
					instances.numInstances());
			for (Instance inst : instances) {
				if (seenInstances.add(inst)) {
					newInstances.add(inst);
				}
			}
			newInstances.compactify();
			return newInstances;
		}
		throw new Exception(
				"The process method should never be called for subsequent batches.");
	}

	private class DuplicateComparator implements Comparator<Instance>,
			Serializable {
		private static final long serialVersionUID = 1L;

		@Override
		public int compare(Instance o1, Instance o2) {
			// Null check
			if (o1 == null)
				if (o2 == null)
					return 0;
				else
					return 1;
			else if (o2 == null)
				return -1;

			// Compare columns defined in the range
			int[] elements = m_SelectCols.getSelection();
			if (elements.length != 0) {
				for (int element : elements) {
					int result = Double.compare(o1.value(element),
							o2.value(element));
					if (result != 0)
						return result;
				}
			} else {
				for (int i = 0; i < o1.numAttributes(); i++) {
					int result = Double.compare(o1.value(i), o2.value(i));
					if (result != 0)
						return result;
				}
			}
			// Otherwise considered a duplicate
			return 0;
		}
	}

	@Override
	public boolean setInputFormat(Instances instanceInfo) throws Exception {
		super.setInputFormat(instanceInfo);
		m_SelectCols.setUpper(instanceInfo.numAttributes() - 1);
		return true;
	}

	/**
	 * Returns the revision string.
	 * 
	 * @return the revision
	 */
	@Override
	public String getRevision() {
		return RevisionUtils.extract("$Revision: 9804 $");
	}

	/**
	 * Main method for running this filter.
	 *
	 * @param args
	 *            arguments for the filter: use -h for help
	 */
	public static void main(String[] args) {
		runFilter(new RemoveCustomDuplicates(), args);
	}
}
