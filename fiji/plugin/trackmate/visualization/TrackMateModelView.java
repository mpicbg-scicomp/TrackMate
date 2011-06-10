package fiji.plugin.trackmate.visualization;

import ij.ImagePlus;
import ij3d.Content;
import ij3d.ContentCreator;
import ij3d.Image3DUniverse;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.jfree.chart.renderer.InterpolatePaintScale;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

import fiji.plugin.trackmate.Feature;
import fiji.plugin.trackmate.InfoTextable;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.TrackMateModel;
import fiji.plugin.trackmate.TrackMateModelChangeListener;
import fiji.plugin.trackmate.segmentation.SegmenterSettings;
import fiji.plugin.trackmate.util.TMUtils;
import fiji.plugin.trackmate.visualization.hyperstack.HyperStackDisplayer;
import fiji.plugin.trackmate.visualization.threedviewer.SpotDisplayer3D;

/**
 * The mother abstract class for spot displayers, that can overlay segmented spots and tracks on top
 * of the image data. 
 * <p>
 * Displayers must implements this abstract class. It offers on top some facilities to store common
 * fields, and can instantiate concrete implementation based on factory design.
 * <p>
 * @author Jean-Yves Tinevez <jeanyves.tinevez@gmail.com> Jan 2011
 */
public abstract class TrackMateModelView implements TMSelectionDisplayer, TrackMateModelViewI, TrackMateModelChangeListener {
	
	/*
	 * FIELDS
	 */
	
	public static final TrackDisplayMode DEFAULT_TRACK_DISPLAY_MODE = TrackDisplayMode.ALL_WHOLE_TRACKS;
	public static final int DEFAULT_TRACK_DISPLAY_DEPTH 			= 10;
	
	/** The default display radius. */
	protected static final float DEFAULT_DISPLAY_RADIUS = 5;
	/** The default color. */
	public static final Color DEFAULT_COLOR = new Color(1f, 0, 1f);
	/** The color used when highlighting spots. */
	public static final Color HIGHLIGHT_COLOR = new Color(0, 1f, 0);
	
	
	/** The model displayed by this class. */
	protected TrackMateModel model;
	
	/** The ratio setting the actual display size of the spots, with respect to the physical radius. */
	protected float radiusRatio = 1.0f;
	
	/** The colorMap. */
	protected InterpolatePaintScale colorMap = InterpolatePaintScale.Jet;
	/** The default color to paint the spots and tracks. */ 
	protected Color color = DEFAULT_COLOR;
	
	/** The track collections emanating from tracking. They are represented as a graph of Spot,
	 * which must be the same objects as for {@link #spots}. */
	protected SimpleWeightedGraph<Spot, DefaultWeightedEdge> trackGraph;
	/** The individual tracks contained in the {@link #trackGraph}. */ 
	protected List<Set<Spot>> tracks;
	/** The track colors. */
	protected Map<Set<Spot>, Color> trackColors;
	/** The Spot lists emanating from segmentation, indexed by the frame index as Integer. */
	protected SpotCollection spots = new SpotCollection();
	/** The subset of Spots retained from {@link #spots} for displaying. */
	protected SpotCollection spotsToShow = new SpotCollection();

	/** The display track mode. */
	protected TrackDisplayMode trackDisplayMode = DEFAULT_TRACK_DISPLAY_MODE;
	/** The display depth: how many track segments will be shown on the track display. */
	protected int trackDisplayDepth = DEFAULT_TRACK_DISPLAY_DEPTH;
	
	/** The list of listener to warn for spot selection change. */
	protected ArrayList<TMSelectionChangeListener> selectionChangeListeners = new ArrayList<TMSelectionChangeListener>();
	
	/** We need to maintain our own selection. */
	private ArrayList<Spot> spotSelection = new ArrayList<Spot>();



	/*
	 * PUBLIC METHODS
	 */
	
	@Override
	public TrackMateModel getModel() {
		return model;
	}
	
	@Override
	public void setModel(TrackMateModel model) {
		if (null != this.model)
			this.model.removeTrackMateModelChangeListener(this);
		this.model = model;
		this.model.addTrackMateModelChangeListener(this);
	}
	
	public void modelChanged(fiji.plugin.trackmate.TrackMateModelChangeEvent event) {
		refresh();
	};

	
	/*
	 * TMSelectionChangeListener
	 */
	
	/**
	 * Add a listener to this displayer that will be notified when the spot selection changes.
	 */
	public void addTMSelectionChangeListener(TMSelectionChangeListener listener) {
		selectionChangeListeners.add(listener);
	}
	
	/**
	 * Remove a listener from the list of the spot selection change listeners list. 
	 * @param listener  the listener to remove
	 * @return  true if the listener was found in the list maintained by 
	 * this displayer and successfully removed.
	 */
	public boolean removeTMSelectionChangeListener(TMSelectionChangeListener listener) {
		return selectionChangeListeners.remove(listener);
	}
	
	/*
	 * tracks
	 */
		
	/**
	 * Set the display mode for tracks. The {@link #refresh()} method must be called to refresh
	 * the display.
	 */
	public void setDisplayTrackMode(TrackDisplayMode mode, int displayDepth) {
		this.trackDisplayMode = mode;
		this.trackDisplayDepth = displayDepth;
	}	
	
	/**
	 * Set the track to be displayed in this displayer.
	 */
	public void setTrackGraph(SimpleWeightedGraph<Spot, DefaultWeightedEdge> trackGraph) {
		this.trackGraph = trackGraph;
		this.tracks = new ConnectivityInspector<Spot, DefaultWeightedEdge>(trackGraph).connectedSets();
		this.trackColors = new HashMap<Set<Spot>, Color>(tracks.size());
		int counter = 0;
		int ntracks = tracks.size();
		for(Set<Spot> track : tracks) {
			trackColors.put(track, colorMap.getPaint((float) counter / (ntracks-1)));
			counter++;
		}
	}
	
	/*
	 * spot collections
	 */
	
	/**
	 * Set the spots that can be displayed by this displayer. Note that calling this method this 
	 * does not actually draw the spots. The spots to be displayed has to be specified 
	 * using {@link #setSpotsToShow(TreeMap)}, and must be a subset from the field passed to this method.
	 * @see #setSpotsToShow(TreeMap)  
	 */
	public void setSpots(SpotCollection spots) {
		this.spots = spots;
	}
	
	/**
	 * Set what spots are to be displayed in this displayer. The list of spot given here must be a subset
	 * of the list passed to the {@link #setSpots(TreeMap)} method.
	 * @see #setSpots(TreeMap) 
	 */
	public void setSpotsToShow(SpotCollection spotsToShow) {
		this.spotsToShow = spotsToShow;
	}

	/**
	 * Set up the ratio used to determine the actual display radius of spots. The spots on the image
	 * will have a radius given by <code> {@link SegmenterSettings#expectedRadius} * ratio </code>.
	 */
	public void setRadiusDisplayRatio(float ratio) {
		this.radiusRatio = ratio;
	}
	
	/*
	 * ABSTRACT METHODS
	 */
	
	/**
	 * Prepare this displayer and render it according to its concrete implementation. Must be called before
	 * adding spots or tracks for displaying. 
	 */
	public abstract void render();
	
	/**
	 * Color all displayed spots according to the feature given. 
	 * If feature is <code>null</code>, then the default color is 
	 * used. The {@link #refresh()} method must be called for display.
	 */
	public abstract void setColorByFeature(final Feature feature);

	/**
	 * Refresh the displayer display with current content.
	 */
	public abstract void refresh();

	/**
	 * Switch visibility of tracks on/off. 
	 */
	public abstract void setTrackVisible(boolean displayTrackSelected);

	/**
	 * Switch visibility of spots on/off.
	 */
	public abstract void setSpotVisible(boolean displaySpotSelected);


	/** 
	 * Switch visibility of spot names on/off.
	 */
	public abstract void setSpotNameVisible(boolean spotNameVisible);	
	
	/**
	 * Remove any overlay (for spots or tracks) from this displayer.
	 */
	public abstract void clear();
	
	/*
	 * PROTECTED METHODS
	 */

	/**
	 * Sub-classers must use this method when the user makes a change in the current selection.
	 * This method will pack and forward changes to registered {@link TMSelectionChangeListener}s.
	 * @param  target  the {@link Spot} to add to / remove from / replace with selection
	 * @param  replace  a boolean flag specifying if the change relate a a modification of the current selection
	 * (<code>false</code>) or if current selection should be erased and replaced by target (<code>true</code>). 
	 */
	protected void spotSelectionChanged(Spot target, boolean replace) {
		HashMap<Spot, Boolean> spotSelectionChange = new HashMap<Spot, Boolean>();

		if (!replace) {

			if (!spotSelection.contains(target)) {
				// Not in the selection, add target to current selection
				spotSelection.add(target);
				spotSelectionChange.put(target, true);

			} else  {
				// Remove target from selection if it was in
				if (!spotSelection.remove(target))
					return;
				spotSelectionChange.put(target, false);
			}

		} else {

			if (null == target) {
				// A null target means that we must empty the current selection
				for (Spot spot : spotSelection) 
					spotSelectionChange.put(spot, false);
				spotSelection.clear();

			} else {

				// Forget previous selection, and set selection to be target
				spotSelection.remove(target); // If target was in selection, so we just have to remove all other
				spotSelectionChange.put(target, true);
				for (Spot spot : spotSelection) 
					spotSelectionChange.put(spot, false);

				spotSelection.clear();
				spotSelection.add(target);
			}
		}
		highlightSpots(spotSelection);
		TMSelectionChangeEvent event = new TMSelectionChangeEvent(this, spotSelectionChange, null);
		fireTMSelectionChange(event);
	}
	
	
	/**
	 * Simply forward the spot selection event to the listeners of this class.
	 */
	protected void fireTMSelectionChange(TMSelectionChangeEvent event) {
		for (TMSelectionChangeListener listener : selectionChangeListeners)
			listener.selectionChanged(event);
	}

	/*
	 * ENUMS
	 */
	
	/**
	 * This enum stores the list of {@link TrackMateModelView} currently available.
	 */
	public static enum ViewType implements InfoTextable {
		THREEDVIEWER_DISPLAYER,
		HYPERSTACK_DISPLAYER;
				
		@Override
		public String toString() {
			switch(this) {
			case HYPERSTACK_DISPLAYER:
				return "HyperStack Displayer";
			case THREEDVIEWER_DISPLAYER:
				return "3D Viewer";
			}
			return null;
		}
		
		@Override
		public String getInfoText() {
			switch(this) {
			case HYPERSTACK_DISPLAYER:
				return "<html>" +
						"This displayer overlays the spots and tracks on the current<br>" +
						"ImageJ hyperstack window." +
						"</html>";
			case THREEDVIEWER_DISPLAYER:
				return "<html>" +
						"This invokes a new 3D viewer (over time) window, which receive a<br>" +
						"8-bit copy of the image data. Spots and tracks are rendered in 3D." +
						"</html>"; 
			}
			return null;
		}

	}

	/**
	 * This enum stores the different display mode for tracks. Note that it might be ignored 
	 * by some displayers.
	 */
	public enum TrackDisplayMode {
		ALL_WHOLE_TRACKS,
		LOCAL_WHOLE_TRACKS,
		LOCAL_BACKWARD_TRACKS,
		LOCAL_FORWARD_TRACKS;
		
		@Override
		public String toString() {
			switch(this) {
			case ALL_WHOLE_TRACKS:
				return "Show all entire tracks";
			case LOCAL_WHOLE_TRACKS:
				return "Show current tracks";
			case LOCAL_BACKWARD_TRACKS:
				return "Show current tracks, only backward";
			case LOCAL_FORWARD_TRACKS:
				return "Show current tracks, only forward";
			}
			return "Not implemented";
		}
		
	}
	
	/*
	 * STATIC METHOD
	 */
	
	/**
	 * Instantiate and render the displayer specified by the given {@link ViewType}, using the data from
	 * the model given. This will render the chosen {@link TrackMateModelView} only with image data.
	 */
	public static TrackMateModelView instantiateView(final ViewType displayerType, final TrackMateModel model) {
		final TrackMateModelView disp;
		Settings settings = model.getSettings();
		switch (displayerType) {
		case THREEDVIEWER_DISPLAYER:
		{ 
			final Image3DUniverse universe = new Image3DUniverse();
			universe.show();
			if (null != settings.imp) {
				if (!settings.imp.isVisible())
					settings.imp.show();
				ImagePlus[] images = TMUtils.makeImageForViewer(settings);
				final Content imageContent = ContentCreator.createContent(
						settings.imp.getTitle(), 
						images, 
						Content.VOLUME, 
						SpotDisplayer3D.DEFAULT_RESAMPLING_FACTOR, 
						0,
						null, 
						SpotDisplayer3D.DEFAULT_THRESHOLD, 
						new boolean[] {true, true, true});
				universe.addContentLater(imageContent);	
			}
			disp = new SpotDisplayer3D(universe);
			disp.render();
			break;

		} 
		case HYPERSTACK_DISPLAYER:
		default:
			{
				disp = new HyperStackDisplayer(model);
				disp.render();
				break;
			}
		}
		return disp;
	}
	
	
}