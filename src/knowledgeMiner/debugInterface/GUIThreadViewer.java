/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.debugInterface;

import java.awt.Color;
import java.awt.Dimension;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

/**
 * 
 * @author Sam Sarjant
 */
public class GUIThreadViewer extends ThreadViewer {
	private static final int NUM_HISTORY = 5;
	private JPanel[] labelPanels_;

	/**
	 * Constructor for a new GUIThreadViewer
	 * 
	 * @param numThreads
	 */
	public GUIThreadViewer(int numThreads) {
		super(numThreads);
		initialise(numThreads);
	}

	/**
	 * Initialises the GUI.
	 */
	private void initialise(int numThreads) {
		JFrame guiWindow = new JFrame("Active Threads");
		JPanel labelPanel = new JPanel();
		labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.Y_AXIS));

		labelPanels_ = new JPanel[numThreads];
		for (int i = 0; i < numThreads; i++) {
			labelPanels_[i] = new JPanel();
			labelPanels_[i].setLayout(new BoxLayout(labelPanels_[i],
					BoxLayout.Y_AXIS));
			labelPanel.add(labelPanels_[i]);

			for (int j = 0; j < NUM_HISTORY; j++) {
				float colour = 0.75f * (NUM_HISTORY - j) / NUM_HISTORY;
				JLabel label = new JLabel();
				label.setForeground(new Color(colour, colour, colour));
				labelPanels_[i].add(label);
			}
		}

		guiWindow.add(labelPanel);
		guiWindow.pack();
		guiWindow.setSize(new Dimension(600, 400));
		guiWindow.setVisible(true);
		guiWindow.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
	}

	@Override
	protected void redraw(String[] outputs, int pos) {
		JPanel labelPanel = labelPanels_[pos];
		for (int i = 0; i < NUM_HISTORY - 1; i++) {
			String priorText = ((JLabel) labelPanel.getComponent(i + 1))
					.getText();
			((JLabel) labelPanel.getComponent(i)).setText(" " + priorText);
		}
		((JLabel) labelPanel.getComponent(NUM_HISTORY - 1))
				.setText(outputs[pos]);
		labelPanel.repaint();
	}

}
