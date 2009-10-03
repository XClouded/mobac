package tac.gui.actions;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import javax.xml.bind.JAXBException;

import tac.data.gpx.GPXTest;
import tac.data.gpx.gpx11.Gpx;
import tac.gui.MainGUI;
import tac.gui.mapview.GpxLayer;
import tac.gui.panels.JGpxPanel;
import tac.program.model.Settings;

public class GpxLoad implements ActionListener {

	JGpxPanel panel;

	public GpxLoad(JGpxPanel panel) {
		super();
		this.panel = panel;
	}

	public void actionPerformed(ActionEvent event) {
		MainGUI.getMainGUI().previewMap.setMapMarkerVisible(true);

		JFileChooser fc = new JFileChooser();
		try {
			File dir = new File(Settings.getInstance().gpxFileChooserDir);
			fc.setCurrentDirectory(dir); // restore the saved directory
		} catch (Exception e) {
		}
		fc.addChoosableFileFilter(new FileFilter() {

			@Override
			public boolean accept(File f) {
				return f.isDirectory() || f.getName().endsWith(".gpx");
			}

			@Override
			public String getDescription() {
				return "GPX 1.1 files (*.gpx)";
			}
		});
		int returnVal = fc.showOpenDialog(MainGUI.getMainGUI());
		if (returnVal != JFileChooser.APPROVE_OPTION)
			return;
		Settings.getInstance().gpxFileChooserDir = fc.getCurrentDirectory().getAbsolutePath();

		try {
			File f = fc.getSelectedFile();
			Gpx gpx = GPXTest.loadGpxFile(f);
			GpxLayer gpxLayer = new GpxLayer(gpx);
			panel.addListEntry(f, gpxLayer);
			MainGUI.getMainGUI().previewMap.mapLayers.add(gpxLayer);
		} catch (JAXBException e) {
			throw new RuntimeException(e);
		}
		MainGUI.getMainGUI().previewMap.repaint();
	}
}
