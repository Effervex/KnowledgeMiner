/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping.quickfire;

import graph.inference.CommonQuery;
import io.ontology.DAGAccess;
import io.ontology.OntologyAccess;
import io.ontology.OntologySocket;
import io.resources.WMIAccess;
import io.resources.WMISocket;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;

import util.wikipedia.WikiParser;
import cyc.OntologyConcept;

/**
 * 
 * @author Sam Sarjant
 */
public class QuickfireMappingReviewer extends JFrame implements WindowListener {
	private static final long serialVersionUID = 3225137008926510379L;
	public static final File MAPPING_FILE = new File("mappings.txt");
	private JLabel articleLabel_;
	private JTextArea articleDetails_;
	private JLabel conceptLabel_;
	private JTextArea conceptDetails_;
	private JLabel thresholdLabel_;
	private JLabel mappingLabel_;

	private int currentIndex_ = -1;
	private String[][] data_;
	private int prevIndex_ = currentIndex_;

	private double[] sumConfs_ = new double[2];
	private int[] counts_ = new int[2];

	private Random random_ = new Random();

	private OntologyAccess cyc_;
	private WMIAccess wmi_;
	private boolean updateDetails_;

	/**
	 * Constructor for a new QuickfireMappingReviewer.
	 */
	public QuickfireMappingReviewer() {
		try {
			data_ = readData();
			double threshold = calculateThreshold();
			int TP = 0;
			int FP = 0;
			int TN = 0;
			int FN = 0;
			for (String[] instance : data_) {
				if (instance[4] != null) {
					if (instance[4].equals("T")) {
						if (Double.parseDouble(instance[3]) >= threshold)
							TP++;
						else
							FN++;
					} else if (instance[4].equals("F")) {
						if (Double.parseDouble(instance[3]) >= threshold)
							FP++;
						else
							TN++;
					}
				}
			}
			float total = TP + FP + TN + FN;
			System.out.println("Total: " + (int) total + " (True: " + (TP + FN)
					+ ", False: " + (FP + TN) + ")");
			System.out.println("True positives: " + (TP / total));
			System.out.println("False positives: " + (FP / total));
			System.out.println("True negatives: " + (TN / total));
			System.out.println("False negatives: " + (FN / total));
			System.out.println();
			System.out.println("Precision: " + (1f * TP / (TP + FP)));
			System.out.println("Recall: " + (1f * TP / (TP + FN)));
			System.out.println("True Negatives: " + (1f * TN / (TN + FP)));
			System.out.println("Accuracy: " + (1f * (TP + TN) / total));
		} catch (Exception e) {
			e.printStackTrace();
		}

		try {
			cyc_ = new DAGAccess(-1);
			wmi_ = new WMIAccess(-1);
		} catch (Exception e) {
			e.printStackTrace();
		}

		setLayout(new GridLayout(2, 1));

		JPanel global = new JPanel();
		global.setLayout(new BoxLayout(global, BoxLayout.X_AXIS));
		add(global);

		JPanel leftTopPanel = new JPanel();
		leftTopPanel.setLayout(new BoxLayout(leftTopPanel, BoxLayout.Y_AXIS));
		conceptLabel_ = new JLabel("CONCEPT");
		conceptLabel_.setAlignmentX(Component.RIGHT_ALIGNMENT);
		leftTopPanel.add(conceptLabel_);
		// leftTopPanel.add(new JSeparator());
		conceptDetails_ = new JTextArea("LOADING...", 8, 40);
		conceptDetails_.setAlignmentX(Component.CENTER_ALIGNMENT);
		conceptDetails_.setEditable(false);
		conceptDetails_.setLineWrap(true);
		JScrollPane conceptScroll = new JScrollPane(conceptDetails_,
				ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		conceptScroll.setPreferredSize(new Dimension(400, 150));
		leftTopPanel.add(conceptScroll);

		global.add(leftTopPanel);

		JLabel equiv = new JLabel(" <=> ");
		equiv.setAlignmentY(Component.TOP_ALIGNMENT);
		global.add(equiv);

		JPanel rightTopPanel = new JPanel();
		rightTopPanel.setLayout(new BoxLayout(rightTopPanel, BoxLayout.Y_AXIS));
		articleLabel_ = new JLabel("ARTICLE");
		articleLabel_.setAlignmentX(Component.LEFT_ALIGNMENT);
		rightTopPanel.add(articleLabel_);
		// rightTopPanel.add(new JSeparator());
		articleDetails_ = new JTextArea("LOADING...", 8, 40);
		articleDetails_.setAlignmentX(Component.CENTER_ALIGNMENT);
		articleDetails_.setEditable(false);
		articleDetails_.setLineWrap(true);
		articleDetails_.setWrapStyleWord(true);
		JScrollPane articleScroll = new JScrollPane(articleDetails_,
				ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		articleScroll.setPreferredSize(new Dimension(400, 150));
		rightTopPanel.add(articleScroll);

		global.add(rightTopPanel);

		JPanel bottomPanel = new JPanel();
		mappingLabel_ = new JLabel("MAPPING");
		bottomPanel.add(mappingLabel_);
		add(bottomPanel);

		thresholdLabel_ = new JLabel("THRESHOLD");
		bottomPanel.add(thresholdLabel_);

		addKeyListener(new VoteKeyListener());
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		addWindowListener(this);
		setFocusable(true);

		// this.setSize(600, 300);
		this.pack();
		this.setVisible(true);
	}

	/**
	 * Reads the data in.
	 * 
	 * @return An array of string arrays representing the data.
	 * @throws Exception
	 *             Should something go awry..
	 */
	private String[][] readData() throws Exception {
		ArrayList<String[]> data = new ArrayList<>();

		BufferedReader reader = new BufferedReader(new FileReader(MAPPING_FILE));
		String input = null;
		while ((input = reader.readLine()) != null) {
			String[] split = input.split("\t+");
			String[] reviewedData = split;
			if (reviewedData.length != 5) {
				reviewedData = new String[5];
				System.arraycopy(split, 0, reviewedData, 0, split.length);
			}
			data.add(reviewedData);

			if (reviewedData[4] != null) {
				int index = (reviewedData[4].equals("T")) ? 0 : 1;
				sumConfs_[index] += Double.parseDouble(reviewedData[3]);
				counts_[index]++;
			}
		}
		reader.close();
		return data.toArray(new String[data.size()][]);
	}

	private void updateDisplay(boolean newIndex) {
		if (currentIndex_ != -1) {
			System.out.println(Arrays.toString(data_[currentIndex_]));
		}
		if (newIndex) {
			prevIndex_ = currentIndex_;
			currentIndex_ = random_.nextInt(data_.length);
		}

		conceptLabel_.setText(data_[currentIndex_][1]);
		conceptDetails_.setText("LOADING...");
		articleLabel_.setText(data_[currentIndex_][2]);
		articleDetails_.setText("LOADING...");
		updateDetails_ = true;
		mappingLabel_.setText("(" + data_[currentIndex_][3] + ")");
		thresholdLabel_.setText("THRESHOLD: " + calculateThreshold());
		repaint();
	}

	private double calculateThreshold() {
		double trueAverage = sumConfs_[0] / counts_[0];
		double falseAverage = sumConfs_[1] / counts_[1];
		return ((trueAverage + falseAverage) / 2);
	}

	public void begin() throws Exception {
		updateDisplay(true);
		while (true) {
			if (updateDetails_) {
				// Concept
				StringBuilder parentage = new StringBuilder();
				OntologySocket cyc = cyc_.requestSocket();
				Collection<OntologyConcept> parents = cyc.quickQuery(
						CommonQuery.MINISA, data_[currentIndex_][1]);
				parents.addAll(cyc.quickQuery(CommonQuery.MINGENLS,
						data_[currentIndex_][1]));
				for (OntologyConcept parent : parents)
					parentage.append(parent + "\n");
				conceptDetails_.setText(parentage.toString());

				// Article
				WMISocket wmi = wmi_.requestSocket();
				String sentence = wmi.getFirstSentence(wmi
						.getArticleByTitle(data_[currentIndex_][2]));
				sentence = WikiParser.cleanAllMarkup(sentence);
				articleDetails_.setText(sentence);

				updateDetails_ = false;
			}
			Thread.sleep(500);
		}
	}

	@Override
	public void windowActivated(WindowEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void windowClosing(WindowEvent arg0) {
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(
					MAPPING_FILE));
			for (String[] data : data_) {
				if (data[4] != null)
					writer.write(data[0] + "\t" + data[1] + "\t" + data[2]
							+ "\t" + data[3] + "\t" + data[4] + "\n");
				else
					writer.write(data[0] + "\t" + data[1] + "\t" + data[2]
							+ "\t" + data[3] + "\t\n");
			}
			writer.close();
			System.out.println("Data written");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void windowClosed(WindowEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void windowDeactivated(WindowEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void windowDeiconified(WindowEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void windowIconified(WindowEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void windowOpened(WindowEvent arg0) {
		// TODO Auto-generated method stub

	}

	public static void main(String[] args) {
		QuickfireMappingReviewer qfr = new QuickfireMappingReviewer();
		try {
			qfr.begin();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private class VoteKeyListener implements KeyListener {

		@Override
		public void keyPressed(KeyEvent e) {
			// TODO Auto-generated method stub
		}

		@Override
		public void keyReleased(KeyEvent e) {
			if (e.getKeyCode() == KeyEvent.VK_UP) {
				data_[currentIndex_][4] = "T";
				sumConfs_[0] += Double.parseDouble(data_[currentIndex_][3]);
				counts_[0]++;
				updateDisplay(true);
			}
			if (e.getKeyCode() == KeyEvent.VK_DOWN) {
				data_[currentIndex_][4] = "F";
				sumConfs_[1] += Double.parseDouble(data_[currentIndex_][3]);
				counts_[1]++;
				updateDisplay(true);
			}
			if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
				// data_[currentIndex_][4] = "F";
				updateDisplay(true);
			}
			if (e.getKeyCode() == KeyEvent.VK_LEFT) {
				currentIndex_ = prevIndex_;
				updateDisplay(false);
			}
		}

		@Override
		public void keyTyped(KeyEvent e) {
			// TODO Auto-generated method stub

		}

	}
}
