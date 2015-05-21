package spim.fiji.plugin;


import ij.ImageJ;
import ij.plugin.PlugIn;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

import java.util.List;

/**
 * Created by Stephan Janosch on 21/05/15.
 *
 * This class is intended to print a message to the user, if registrations are reasonable or not. Sometime oversegmentation
 * happens because of default parameters and more that 1 million interest points are detected. This plugin should give the
 * user a simple answer if the detections are normal.
 */
public class Detection_Verfication implements PlugIn {



    @Override
    public void run(String arg) {

        // ask for everything but the channels
        final LoadParseQueryXML result = new LoadParseQueryXML();

        if ( !result.queryXML( "load xml", false, false, false, false ) )
            return;



        SpimData2 data = result.getData();

        final List< ViewId > viewIds = SpimData2.getAllViewIdsSorted( data, result.getViewSetupsToProcess(), result.getTimePointsToProcess() );
        final ViewInterestPoints vip = data.getViewInterestPoints();
        List<Channel> channels=result.getChannelsToProcess();

        for(final Channel channel:channels) {

            String[] labels = Interest_Point_Registration.getAllInterestPointLabelsForChannel(data,viewIds,channel);

            for (final ViewId viewId : viewIds) {
                final ViewDescription vd = data.getSequenceDescription().getViewDescription(viewId);

                //same channel?
                if ( !vd.isPresent() || vd.getViewSetup().getChannel().getId() != channel.getId() )
                    continue;

                final ViewInterestPointLists vipl = vip.getViewInterestPointLists(viewId);

                for (String label:labels)
                {

                    InterestPointList interestPointList = vipl.getInterestPointList(label);
                    int interestPointCount =interestPointList.getInterestPoints().size();

                    //now do something reasonable with this count, maybe check against some upper threshold

                    System.out.println(channel.getName()+" "+vd.getViewSetup().getAngle().getName()+" "+label+": "+interestPointCount);
                }
            }
        }
    }

    public static void main( String[] args )
    {
        new ImageJ();
        new Detection_Verfication().run( null );
    }

}
