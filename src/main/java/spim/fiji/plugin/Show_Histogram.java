package spim.fiji.plugin;

import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.KDTree;
import net.imglib2.RealPoint;
import net.imglib2.neighborsearch.KNearestNeighborSearchOnKDTree;
import spim.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.plugin.thinout.ChannelProcessThinOut;
import spim.fiji.plugin.thinout.Histogram;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Created by Stephan Janosch on 01/07/15.
 */
public class Show_Histogram implements PlugIn {

    public static boolean[] defaultShowHistogram;
    public static int[] defaultSubSampling;
    public static int defaultSubSamplingValue = 1;




    public static Histogram plotHistogram( final SpimData2 spimData, final List< ViewId > viewIds, final ChannelProcessThinOut channel )
    {
        final ViewInterestPoints vip = spimData.getViewInterestPoints();

        // list of all distances
        final ArrayList< Double > distances = new ArrayList< Double >();
        final Random rnd = new Random( System.currentTimeMillis() );
        String unit = null;

        for ( final ViewId viewId : viewIds )
        {
            final ViewDescription vd = spimData.getSequenceDescription().getViewDescription( viewId );

            if ( !vd.isPresent() || vd.getViewSetup().getChannel().getId() != channel.getChannel().getId() )
                continue;

            final ViewInterestPointLists vipl = vip.getViewInterestPointLists( viewId );
            final InterestPointList ipl = vipl.getInterestPointList( channel.getLabel() );

            final VoxelDimensions voxelSize = vd.getViewSetup().getVoxelSize();

            if ( ipl.getInterestPoints() == null )
                ipl.loadInterestPoints();

            if ( unit == null )
                unit = vd.getViewSetup().getVoxelSize().unit();

            // assemble the list of points
            final List<RealPoint> list = new ArrayList< RealPoint >();

            for ( final InterestPoint ip : ipl.getInterestPoints() )
            {
                list.add ( new RealPoint(
                        ip.getL()[ 0 ] * voxelSize.dimension( 0 ),
                        ip.getL()[ 1 ] * voxelSize.dimension( 1 ),
                        ip.getL()[ 2 ] * voxelSize.dimension( 2 ) ) );
            }

            // make the KDTree
            final KDTree< RealPoint > tree = new KDTree< RealPoint >( list, list );

            // Nearest neighbor for each point
            final KNearestNeighborSearchOnKDTree< RealPoint > nn = new KNearestNeighborSearchOnKDTree< RealPoint >( tree, 2 );

            for ( final RealPoint p : list )
            {
                // every n'th point only
                if ( rnd.nextDouble() < 1.0 / (double)channel.getSubsampling() )
                {
                    nn.search( p );

                    // first nearest neighbor is the point itself, we need the second nearest
                    distances.add( nn.getDistance( 1 ) );
                }
            }
        }

        final Histogram h = new Histogram( distances, 100, "Distance Histogram [Channel=" + channel.getChannel().getName() + "]", unit  );
        h.showHistogram();
        IOFunctions.println("Channel " + channel.getChannel().getName() + ": min distance=" + h.getMin() + ", max distance=" + h.getMax());
        return h;
    }

    public static ArrayList< ChannelProcessThinOut > getChannelsAndLabels(
            final SpimData2 spimData,
            final List< ViewId > viewIds )
    {
        // build up the dialog
        final GenericDialog gd = new GenericDialog( "Choose segmentations to show in histogram" );

        final List< Channel > channels = SpimData2.getAllChannelsSorted( spimData, viewIds );
        final int nAllChannels = spimData.getSequenceDescription().getAllChannelsOrdered().size();

        if ( Interest_Point_Registration.defaultChannelLabels == null || Interest_Point_Registration.defaultChannelLabels.length != nAllChannels )
            Interest_Point_Registration.defaultChannelLabels = new int[ nAllChannels ];

        if ( defaultShowHistogram == null || defaultShowHistogram.length != channels.size() )
        {
            defaultShowHistogram = new boolean[ channels.size() ];
            for ( int i = 0; i < channels.size(); ++i )
                defaultShowHistogram[ i ] = true;
        }

        if ( defaultSubSampling == null || defaultSubSampling.length != channels.size() )
        {
            defaultSubSampling = new int[ channels.size() ];
            for ( int i = 0; i < channels.size(); ++i )
                defaultSubSampling[ i ] = defaultSubSamplingValue;
        }

        // check which channels and labels are available and build the choices
        final ArrayList< String[] > channelLabels = new ArrayList< String[] >();
        int j = 0;
        for ( final Channel channel : channels )
        {
            final String[] labels = Interest_Point_Registration.getAllInterestPointLabelsForChannel(
                    spimData,
                    viewIds,
                    channel,
                    "thin out" );

            if ( Interest_Point_Registration.defaultChannelLabels[ j ] >= labels.length )
                Interest_Point_Registration.defaultChannelLabels[ j ] = 0;

            String ch = channel.getName().replace( ' ', '_' );
            gd.addCheckbox( "Channel_" + ch + "_Display_distance_histogram", defaultShowHistogram[ j ] );
            gd.addChoice( "Channel_" + ch + "_Interest_points", labels, labels[ Interest_Point_Registration.defaultChannelLabels[ j ] ] );
            gd.addNumericField( "Channel_" + ch + "_Subsample histogram", defaultSubSampling[ j ], 0, 5, "times" );

            channelLabels.add( labels );
            ++j;
        }

        gd.showDialog();

        if ( gd.wasCanceled() )
            return null;

        // assemble which channels have been selected with with label
        final ArrayList< ChannelProcessThinOut > channelsToProcess = new ArrayList< ChannelProcessThinOut >();
        j = 0;

        for ( final Channel channel : channels )
        {
            final boolean showHistogram = defaultShowHistogram[ j ] = gd.getNextBoolean();
            final int channelChoice = Interest_Point_Registration.defaultChannelLabels[ j ] = gd.getNextChoiceIndex();
            final int subSampling = defaultSubSampling[ j ] = (int)Math.round( gd.getNextNumber() );

            if ( channelChoice < channelLabels.get( j ).length - 1 )
            {
                String label = channelLabels.get( j )[ channelChoice ];

                if ( label.contains( Interest_Point_Registration.warningLabel ) )
                    label = label.substring( 0, label.indexOf( Interest_Point_Registration.warningLabel ) );

                channelsToProcess.add( new ChannelProcessThinOut( channel, label, "", showHistogram, subSampling ) );
            }

            ++j;
        }

        return channelsToProcess;
    }

    public void run(String arg) {
        final LoadParseQueryXML xml = new LoadParseQueryXML();

        if (!xml.queryXML("", true, false, true, true))
            return;

        final SpimData2 data = xml.getData();
        final List<ViewId> viewIds = SpimData2.getAllViewIdsSorted(data, xml.getViewSetupsToProcess(), xml.getTimePointsToProcess());
//        final List<Channel> channels= SpimData2.getAllChannelsSorted(data,viewIds);


//        // ask which channels have the objects we are searching for
        final List<ChannelProcessThinOut> channels = getChannelsAndLabels(data, viewIds);

        for ( final ChannelProcessThinOut channel : channels )
            if ( channel.showHistogram() )
                plotHistogram( data, viewIds, channel );

    }

    public static void main( final String[] args )
    {
        GenericLoadParseQueryXML.defaultXMLfilename = "/Volumes/LaCie/150424_OP227_TubeScaleA2/Worm1_G1/dataset_detect.xml";
        new Show_Histogram().run( null );
    }
}