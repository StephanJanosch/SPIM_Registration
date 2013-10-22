package fiji.plugin;

import ij.ImageJ;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Font;
import java.awt.Label;
import java.awt.TextField;
import java.awt.event.TextEvent;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import mpicbg.spim.data.sequence.IntegerPattern;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.io.IOFunctions;

import org.xml.sax.SAXException;

import fiji.datasetmanager.StackList;
import fiji.spimdata.SpimDataBeads;
import fiji.spimdata.XmlIo;
import fiji.spimdata.XmlIoSpimDataBeads;
import fiji.util.gui.GenericDialogPlus;

public class LoadParseQueryXML 
{
	public static String defaultXMLfilename = "/Users/preibischs/Documents/Microscopy/SPIM/HisYFP-SPIM/example_fromdialog_old.xml";
	
	public static Color good = new Color( 0, 139, 14 );
	public static Color warning = new Color( 255, 100, 0 );
	public static Color error = new Color( 255, 0, 0 );
	public static Color neutral = new Color( 0, 0, 0 );
	
	public static Font font = new Font( Font.SANS_SERIF, Font.BOLD + Font.ITALIC, 14 );
	public static Font smallFont = new Font( Font.SANS_SERIF, Font.ITALIC, 11 );
	
	public static String goodMsg = "The selected XML file was parsed successfully";
	public static String warningMsg = "The selected file does not appear to be an xml. Press OK to try to parse anyways.";
	public static String errorMsg = "An ERROR occured parsing this XML file! Please select a different XML (see log)";
	public static String neutralMsg = "No XML file selected.";
	
	public static String[] tpChoice = new String[]{ "All Timepoints", "Single Timepoint (Select from List)", "Multiple Timepoints (Select from List)", "Range of Timepoints (Specify by Name)" };
	public static int defaultTPChoice = 1;
	public static int defaultTimePointIndex = 0;
	public static boolean[] defaultTimePointIndices = null;
	public static String defaultTimePointString = null;
	
	public class XMLParseResult
	{
		String message;
		Color color;
		
		public SpimDataBeads data;
		public String xmlfilename;
		public int timepointChoiceIndex;
		public ArrayList< Integer > timepointindices;
	}
	
	/**
	 * Asks the user for a valid XML (real time parsing)
	 * 
	 * @param askForTimepoints - ask the user if he/she wants to select a subset of timepoints, otherwise all timepoints are selected
	 * @return
	 */
	public XMLParseResult queryXML( final boolean askForTimepoints )
	{
		// try parsing if it ends with XML
		XMLParseResult xmlResult = tryParsing( defaultXMLfilename, false );
		
		final GenericDialogPlus gd = new GenericDialogPlus( "Select Dataset" );
		
		gd.addFileField( "Select_XML", defaultXMLfilename, 65 );
		gd.addMessage( xmlResult.message, font, xmlResult.color );
		addListeners( gd, (TextField)gd.getStringFields().lastElement(), (Label)gd.getMessage() );
		
		if ( askForTimepoints )
		{
			gd.addMessage( "" );
			gd.addChoice( "Process", tpChoice, tpChoice[ defaultTPChoice ] );
		}
		
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return null;
		
		String xmlFilename = defaultXMLfilename = gd.getNextString();
		
		// try to parse the file anyways
		xmlResult = tryParsing( xmlFilename, true );

		if ( askForTimepoints )
			xmlResult.timepointChoiceIndex = defaultTPChoice = gd.getNextChoiceIndex();
		else
			xmlResult.timepointChoiceIndex = 0; // all timepoints

		// fill up timepoints (if all there is no further dialog)
		queryTimepoints( xmlResult );

		return xmlResult;
	}
	
	public boolean queryTimepoints( final XMLParseResult xmlResult )
	{	
		final List< TimePoint > tpList = xmlResult.data.getSequenceDescription().getTimePoints().getTimePointList();
		
		if ( xmlResult.timepointChoiceIndex == 1 )
		{
			// choose a single timepoint
			
			final String[] timepoints = buildTimepointList( tpList );
			
			if ( defaultTimePointIndex >= timepoints.length )
				defaultTimePointIndex = 0;
			
			final GenericDialog gd = new GenericDialog( "Select Single Timepoint" );
			gd.addChoice( "Process", timepoints, timepoints[ defaultTimePointIndex ] );
			
			gd.showDialog();
			
			if ( gd.wasCanceled() )
				return false;
			
			xmlResult.timepointindices = new ArrayList< Integer >();
			xmlResult.timepointindices.add( tpList.get( defaultTimePointIndex = gd.getNextChoiceIndex() ).getId() );
		}
		else if ( xmlResult.timepointChoiceIndex == 2 )
		{
			// choose multiple timepoints
			
			final String[] timepoints = buildTimepointList( tpList );
			
			if ( defaultTimePointIndices == null || defaultTimePointIndices.length != timepoints.length )
			{
				defaultTimePointIndices = new boolean[ timepoints.length ];
				defaultTimePointIndices[ 0 ] = true;
				for ( int i = 1; i < timepoints.length; ++i )
					defaultTimePointIndices[ i ] = false;
			}
			
			final GenericDialog gd = new GenericDialog( "Select Multiple Timepoints" );
			
			gd.addMessage( "" );
			for ( int i = 0; i < timepoints.length; ++i )
				gd.addCheckbox( timepoints[ i ], defaultTimePointIndices[ i ] );
			gd.addMessage( "" );

			StackList.addScrollBars( gd );			
			gd.showDialog();
			
			if ( gd.wasCanceled() )
				return false;

			xmlResult.timepointindices = new ArrayList< Integer >();
			for ( int i = 0; i < timepoints.length; ++i )
			{
				if ( gd.getNextBoolean() )
				{
					xmlResult.timepointindices.add( tpList.get( i ).getId() );
					defaultTimePointIndices[ i ] = true;
				}
				else
				{
					defaultTimePointIndices[ i ] = false;					
				}
			}
		} 
		else if ( xmlResult.timepointChoiceIndex == 3 )
		{
			final String[] timepoints = buildTimepointList( tpList );
			
			if ( defaultTimePointString == null || defaultTimePointString.length() == 0 )
			{
				defaultTimePointString = tpList.get( 0 ).getName();
				
				for ( int i = 1; i < Math.min( timepoints.length, 3 ); ++i )
					defaultTimePointString += "," + tpList.get( i ).getName();
			}
			
			final GenericDialog gd = new GenericDialog( "Select Range of Timepoints" );
			
			gd.addMessage( "" );
			gd.addStringField( "Process_Timepoints", defaultTimePointString, 30 );
			gd.addMessage( "" );
			gd.addMessage( "Available Timepoints:" );
			
			String allTps = timepoints[ 0 ];
			
			for ( int i = 1; i < timepoints.length; ++i )
				allTps += "\n" + timepoints[ i ];
			
			gd.addMessage( allTps, smallFont );
			
			StackList.addScrollBars( gd );
			gd.showDialog();
			
			if ( gd.wasCanceled() )
				return false;
			
			try 
			{
				final ArrayList< Integer > timepointList = IntegerPattern.parseIntegerString( defaultTimePointString = gd.getNextString() );
				xmlResult.timepointindices = new ArrayList< Integer >();
				
				for ( final int tp : timepointList )
				{
					boolean found = false;
					
					for ( int i = 0; i < tpList.size() && !found; ++i )
					{
						if ( tp == Integer.parseInt( tpList.get( i ).getName() ) )
						{
							xmlResult.timepointindices.add( tpList.get( i ).getId() );
							found = true;
						}
					}
					
					if ( !found )
						IOFunctions.println( "Timepoint " + tp + " not part of the list of timepoints. Ignoring it." );
				}				
			} 
			catch (ParseException e) 
			{
				IOFunctions.println( "Cannot parse pattern '" + defaultTimePointString + "': " + e );
				defaultTimePointString = null;
				return false;
			}
		} 
		else
		{
			xmlResult.timepointindices = new ArrayList< Integer >();
			
			for ( int i = 0; i < tpList.size(); ++i )
				xmlResult.timepointindices.add( tpList.get( i ).getId() );				
		}
		
		if ( xmlResult.timepointindices.size() == 0 )
		{
			IOFunctions.println( "List of timepoints is empty. Stopping." );
			xmlResult.timepointindices = null;
			return false;
		}

		String allTp = tpList.get( xmlResult.timepointindices.get( 0 ) ).getName();
		
		for ( int i = 1; i < xmlResult.timepointindices.size(); ++i )
			allTp += "," + tpList.get( xmlResult.timepointindices.get( i ) ).getName();
		
		IOFunctions.println( "Timepoints selected: " + allTp );
		
		return true;
	}
	
	protected String[] buildTimepointList( final List< TimePoint > tpList )
	{
		final String[] timepoints = new String[ tpList.size() ];
		
		for ( int i = 0; i < timepoints.length; ++i )
			timepoints[ i ] = "Timepoint " + tpList.get( i ).getName();
		
		return timepoints;
	}
	
	public XMLParseResult tryParsing( final String xmlfile, final boolean parseAllTypes )
	{
		final XMLParseResult xml = new XMLParseResult();
		
		xml.xmlfilename = xmlfile;
		xml.message = neutralMsg;
		xml.color = neutral;
		xml.data = null;
		
		if ( parseAllTypes || ( !parseAllTypes && xmlfile.endsWith( ".xml" ) ) )
		{
			try 
			{
				xml.data = parseXML( xmlfile );
				
				int countMissingViews = 0;
				
				for ( final ViewDescription<?, ?> v : xml.data.getSequenceDescription().getViewDescriptions() )
					if ( !v.isPresent() )
						++countMissingViews;

				xml.message = goodMsg + " [" + xml.data.getSequenceDescription().numTimePoints() + " timepoints, " + 
											   xml.data.getSequenceDescription().numViewSetups() + " viewsetups, " + 
											   countMissingViews + " missing views]";
				xml.color = good;
			}
			catch ( final Exception e )
			{
				xml.message = errorMsg;
				xml.color = error;

				IOFunctions.println( "Cannot parse '" + xmlfile + "': " + e );
			}
		}
		else if ( xmlfile.length() > 0 )
		{
			xml.message = warningMsg;
			xml.color = warning;
		}	
		
		return xml;
	}
	
	public SpimDataBeads parseXML( final String xmlFilename ) throws ParserConfigurationException, SAXException, IOException, InstantiationException, IllegalAccessException, ClassNotFoundException
	{
		final XmlIoSpimDataBeads io = XmlIo.createDefaultIo();
		return io.load( xmlFilename );
	}
	
	protected void addListeners( final GenericDialog gd, final TextField tf, final Label label )
	{
		gd.addDialogListener( new DialogListener()
		{
			@Override
			public boolean dialogItemChanged( final GenericDialog dialog, final AWTEvent e )
			{
				if ( e instanceof TextEvent && e.getID() == TextEvent.TEXT_VALUE_CHANGED && e.getSource() == tf )
				{
					final String xmlFilename = tf.getText();
					
					// try parsing if it ends with XML
					final XMLParseResult xmlResult = tryParsing( xmlFilename, false );
					
					if ( label.getText() != xmlResult.message )
					{
						System.out.println( "setting" );
						label.setText( xmlResult.message );
						label.setForeground( xmlResult.color );
					}
				}
				return true;
			}
		} );		
	}

	public static void main( String args[] )
	{
		new ImageJ();
		IOFunctions.printIJLog = true;
	
		final LoadParseQueryXML lpq = new LoadParseQueryXML();
		final XMLParseResult xmlResult = lpq.queryXML( true );
		
		for ( final int i : xmlResult.timepointindices )
			System.out.println( i );
	
	}
}
