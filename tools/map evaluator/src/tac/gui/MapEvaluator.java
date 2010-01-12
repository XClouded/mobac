package tac.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import tac.StartTAC;
import tac.gui.components.LineNumberedPaper;
import tac.gui.mapview.LogPreviewMap;
import tac.gui.mapview.ReferenceMapMarker;
import tac.mapsources.BeanShellMapSource;
import tac.mapsources.impl.Google;
import tac.mapsources.impl.OsmMapSources;
import tac.program.DirectoryManager;
import tac.program.Logging;
import tac.program.TACInfo;
import tac.program.model.Settings;
import tac.program.tilestore.TileStore;
import tac.utilities.TACExceptionHandler;
import tac.utilities.Utilities;
import bsh.EvalError;

public class MapEvaluator extends JFrame {

	private static MapEvaluator INSTANCE;

	protected Logger log;
	private final LogPreviewMap previewMap;
	private JSplitPane splitPane;
	private final LineNumberedPaper mapSourceEditor;

	public MapEvaluator() throws HeadlessException {
		super(TACInfo.getCompleteTitle());
		log = Logger.getLogger(this.getClass());
		addWindowListener(new MEWindowAdapter());
		setMinimumSize(new Dimension(300, 300));
		setLayout(new BorderLayout());
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		previewMap = new LogPreviewMap();
		previewMap.setMapMarkerVisible(true);
		previewMap.addMapMarker(new ReferenceMapMarker(Color.RED, 1, 2));
		mapSourceEditor = new LineNumberedPaper(3, 60);
		try {
			String code = Utilities.loadTextResource("bsh/default.bsh");
			mapSourceEditor.setText(code);
		} catch (IOException e) {
			log.error("", e);
		}
		JPanel bottomPanel = new JPanel(new BorderLayout());
		JToolBar toolBar = new JToolBar("Toolbar");
		addButtons(toolBar);
		bottomPanel.setMinimumSize(new Dimension(200, 100));

		JScrollPane editorScrollPane = new JScrollPane(mapSourceEditor);
		bottomPanel.add(toolBar, BorderLayout.NORTH);
		bottomPanel.add(editorScrollPane, BorderLayout.CENTER);
		splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, previewMap, bottomPanel);
		add(splitPane, BorderLayout.CENTER);
		setSize(800, 600);
		setExtendedState(JFrame.MAXIMIZED_BOTH);
		INSTANCE = this;
	}

	private void addButtons(JToolBar toolBar) {
		JButton button = null;

		button = new JButton("Reset", Utilities.loadResourceImageIcon("new-icon.png"));
		button.setToolTipText("Reset custom code editor to one of several templates");
		button.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				try {
					String[] options = { "Empty", "OpenStreetMap Mapnik", "Yahoo", "Microsoft Maps" };
					int a = JOptionPane.showOptionDialog(MapEvaluator.this,
							"Please select an template", "Select template", 0,
							JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
					String code = "";
					switch (a) {
					case (0):
						code = Utilities.loadTextResource("bsh/empty.bsh");
						break;
					case (1):
						code = Utilities.loadTextResource("bsh/osm.bsh");
						break;
					case (2):
						code = Utilities.loadTextResource("bsh/yahoo.bsh");
						break;
					case (3):
						code = Utilities.loadTextResource("bsh/bing.bsh");
						break;
					}

					mapSourceEditor.setText(code);
				} catch (IOException e) {
					log.error("", e);
				}
			}
		});
		toolBar.add(button);

		button = new JButton("Load", Utilities.loadResourceImageIcon("open-icon.png"));
		button.setToolTipText("Load custom code from file \"mapsource.bsh\"");
		button.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				try {
					BufferedReader br = new BufferedReader(new InputStreamReader(
							new FileInputStream("mapsource.bsh")));
					StringWriter sw = new StringWriter();
					String line = br.readLine();
					while (line != null) {
						sw.write(line + "\n");
						line = br.readLine();
					}
					br.close();
					mapSourceEditor.setText(sw.toString());
				} catch (IOException e) {
					log.error("", e);
					JOptionPane.showMessageDialog(MapEvaluator.this,
							"Error reading code from file:\n" + e.getMessage(), "Loading failed",
							JOptionPane.ERROR_MESSAGE);
				}
			}
		});
		toolBar.add(button);

		button = new JButton("Save", Utilities.loadResourceImageIcon("save-icon.png"));
		button.setToolTipText("Save custom code to file \"mapsource.bsh\"");
		button.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				try {
					BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
							new FileOutputStream("mapsource.bsh")));
					bw.write(mapSourceEditor.getText());
					bw.close();
				} catch (IOException e) {
					log.error("", e);
					JOptionPane.showMessageDialog(MapEvaluator.this,
							"Error writing code to disk:\n" + e.getMessage(), "Saving failed",
							JOptionPane.ERROR_MESSAGE);
				}
			}
		});
		toolBar.add(button);

		button = new JButton("Execute code", Utilities.loadResourceImageIcon("check-icon.png"));
		button.setToolTipText("Switch to custom map source (as defined by the custom code)");
		button.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				executeCode();
			}
		});
		toolBar.add(button);

		button = new JButton("Google Maps", Utilities.loadResourceImageIcon("google-icon.png"));
		button.setToolTipText("Switch back to predefined Google Maps mapsource");
		button.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				previewMap.setMapSource(new Google.GoogleMaps());
			}
		});
		toolBar.add(button);

		button = new JButton("OSM", Utilities.loadResourceImageIcon("osm-icon.png"));
		button.setToolTipText("Switch back to predefined OpenStreetMap mapsource");
		button.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				previewMap.setMapSource(new OsmMapSources.Mapnik());
			}
		});
		toolBar.add(button);
		button = new JButton("Tile info", Utilities.loadResourceImageIcon("info-icon.png"));
		button.setToolTipText("Show/hide tile info");
		button.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				previewMap.setTileGridVisible(!previewMap.isTileGridVisible());
			}
		});
		toolBar.add(button);
	}

	private void executeCode() {
		try {
			BeanShellMapSource testMapSource = new BeanShellMapSource(mapSourceEditor.getText());
			if (testMapSource.testCode()) {
				previewMap.setMapSource(testMapSource);
				return;
			}
			JOptionPane.showMessageDialog(this, "Error in custom code: result is null",
					"Error in custom code", JOptionPane.ERROR_MESSAGE);
		} catch (EvalError e) {
			log.error("", e);
			JOptionPane.showMessageDialog(this, "Error in custom code: \n" + e.getMessage(),
					"Error in custom code", JOptionPane.ERROR_MESSAGE);
		} catch (Exception e) {
			Throwable cause = e.getCause();
			if (cause instanceof EvalError) {
				log.error("", cause);
				JOptionPane.showMessageDialog(this,
						"Error in custom code: \n" + cause.getMessage(), "Error in custom code",
						JOptionPane.ERROR_MESSAGE);
			} else {
				TACExceptionHandler.processException(e);
			}
		}
	}

	public static void log(String msg) {
		INSTANCE.previewMap.addLog(msg);
	}

	private class MEWindowAdapter extends WindowAdapter {

		@Override
		public void windowOpened(WindowEvent e) {
			splitPane.setDividerLocation(0.8);
		}

		public void windowClosing(WindowEvent event) {
			TileStore.getInstance().closeAll(true);
		}
	}

	public static void main(String[] args) {
		StartTAC.setLookAndFeel();
		TACInfo.PROG_NAME = "TAC Map Evaluator";
		TACExceptionHandler.registerForCurrentThread();
		TACExceptionHandler.installToolkitEventQueueProxy();
		TACInfo.initialize();
		Logging.configureConsoleLogging(Level.TRACE, Logging.ADVANCED_LAYOUT);
		DirectoryManager.initialize();
		try {
			Settings.load();
		} catch (Exception e) {
			// Load settings.xml only if it exists
		}
		TileStore.initialize();
		new MapEvaluator().setVisible(true);
	}
}
