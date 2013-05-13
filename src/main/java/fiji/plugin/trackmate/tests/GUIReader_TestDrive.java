package fiji.plugin.trackmate.tests;

import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.TrackMate;
import fiji.plugin.trackmate.gui.GuiReader;
import fiji.plugin.trackmate.gui.TrackMateWizard;
import fiji.plugin.trackmate.gui.TrackMateGUIController;
import fiji.plugin.trackmate.gui.descriptors.LoadDescriptor;
import fiji.plugin.trackmate.gui.panels.ConfigureViewsPanel;
import ij.IJ;

import java.io.File;

public class GUIReader_TestDrive {
	
	
	public static void main(String args[]) {

		File file;
		if (IJ.isWindows()) {
			file = new File("E:/Users/JeanYves/Desktop/Data/FakeTracks.xml");
		} else {
			file = new File("/Users/tinevez/Desktop/Data/FakeTracks.xml");
		}
		
		ij.ImageJ.main(args);
		
		TrackMate trackmate = new TrackMate();
		trackmate.setLogger(Logger.DEFAULT_LOGGER);

		TrackMateGUIController controller = new TrackMateGUIController(trackmate) {
			@Override
			public TrackMateWizard getWizard() {
				logger = Logger.DEFAULT_LOGGER;
				return super.getWizard();
			}
		};
		TrackMateWizard wizard = controller.getWizard();
		
		wizard.showDescriptorPanelFor(LoadDescriptor.DESCRIPTOR);
		
		trackmate.setLogger(Logger.DEFAULT_LOGGER);
		
		GuiReader greader = new GuiReader(wizard) {
			@Override
			public void loadFile(File file) {
				logger = Logger.DEFAULT_LOGGER;
				super.loadFile(file);
			}
		};
		file = greader.askForFile(file);

		System.out.println("Opening file: "+file.getAbsolutePath());		
		
		greader.loadFile(file);
		trackmate = greader.getPlugin();
		
		wizard.showDescriptorPanelFor(ConfigureViewsPanel.DESCRIPTOR);
		
	}

}