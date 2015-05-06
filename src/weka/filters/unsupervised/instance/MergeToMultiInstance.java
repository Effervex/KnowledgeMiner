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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Vector;

import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Option;
import weka.core.OptionHandler;
import weka.core.Range;
import weka.core.RelationalLocator;
import weka.core.Utils;
import weka.filters.Filter;
import weka.filters.SimpleBatchFilter;
import weka.filters.UnsupervisedFilter;

/**
 * <!-- globalinfo-start --> Merges instances into multi-instance format by
 * merging equal instances according to an equality criteria.
 * <p/>
 * <!-- globalinfo-end -->
 * 
 * <!-- options-start --> Valid options are:
 * <p/>
 * 
 * <pre>
 * -I &lt;col&gt;
 *  Defines the attributes to compare for equality.
 * </pre>
 * 
 * <!-- options-end -->
 *
 * @author Sam Sarjant
 * @version $Revision: 1$
 */
public class MergeToMultiInstance extends SimpleBatchFilter implements
		OptionHandler, UnsupervisedFilter {
	private static final long serialVersionUID = 1L;

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
	 *  Defines the attributes to use for equality checking. (Default first)
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

	@Override
	protected Instances determineOutputFormat(Instances arg0) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String globalInfo() {
		return "Merges equal instances into relational bags "
				+ "(according to specified equality attributes).";
	}

	@Override
	protected Instances process(Instances input) throws Exception {
		Instances output = initOutputInstances(input);

		// Run through each instance, merging where applicable
		// Sort the instances according the attribute first
		for (int index : m_SelectCols.getSelection())
			input.sort(index);
		// Run through instances one-by-one, merging where appropriate
		Instance relInstance = null;
		double[] equality = null;
		int id = 0;
		for (Iterator<Instance> iter = input.iterator(); iter.hasNext();) {
			Instance instance = iter.next();
			double[] thisInst = asEqualityArray(instance);
			if (Arrays.equals(thisInst, equality)) {
				// The equality criterion matches, add to bag
				relInstance.attribute(1).relation();
				// TODO
			}
			if (relInstance == null || !Arrays.equals(equality, thisInst)) {
				// if no rel instance defined or the next instance differs, add
				// a new rel instance.
				relInstance = new DenseInstance(output.numAttributes());
				relInstance.setValue(0, id++);
				// TODO
				// relInstance.setValue(1, bag);
				relInstance.setValue(2, instance.classValue());
				output.add(relInstance);
				equality = thisInst;
			} else {
				// Otherwise, the equality criterion matches
			}

		}

		return output;
	}

	private double[] asEqualityArray(Instance instance) {
		double[] equalityArray = new double[m_SelectCols.getSelection().length];
		int i = 0;
		for (int index : m_SelectCols.getSelection())
			equalityArray[i++] = instance.value(index);
		return equalityArray;
	}

	/**
	 * Initialises the output instance format.
	 *
	 * @param input
	 *            The input format to alter.
	 * @return The output format.
	 */
	private Instances initOutputInstances(Instances input) {
		String name = input.relationName() + "_relational";
		ArrayList<Attribute> attributes = new ArrayList<>(3);
		attributes.add(new Attribute("id"));
		attributes.add(new Attribute("bag"));
		attributes.add(new Attribute("class"));
		int capacity = input.numInstances();
		Instances output = new Instances(name, attributes, capacity);
		output.setClassIndex(2);
		return output;
	}

}
