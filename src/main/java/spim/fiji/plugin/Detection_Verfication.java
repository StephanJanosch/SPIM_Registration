package spim.fiji.plugin;


import ij.IJ;
import ij.ImageJ;
import ij.plugin.PlugIn;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
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

    private static final int UPPER_THRESHOLD = 20000;
    private static final int ZERO = 0;

    @Override
    public void run(String arg) {

        // ask for everything but the channels
        final LoadParseQueryXML result = new LoadParseQueryXML();

        if ( !result.queryXML( "load xml", false, false, false, false ) )
            return;

        if (isDetectionReasonable(result))
            IJ.log("Your XML seems reasonable");
        else
            IJ.log("Your XML might have problems: "+result.getXMLFileName());
    }

    private static boolean isDetectionReasonable(LoadParseQueryXML result) {
        boolean isReasonable = true;
        int currentStep =0;
        int stepsToDo =0;
        int channelMultiplier =result.getTimePointsToProcess().size()*result.getAnglesToProcess().size()*result.getIlluminationsToProcess().size();
        SpimData2 data = result.getData();

        final List< ViewId > viewIds = SpimData2.getAllViewIdsSorted( data, result.getViewSetupsToProcess(), result.getTimePointsToProcess() );
        final ViewInterestPoints vip = data.getViewInterestPoints();
        List<Channel> channels=result.getChannelsToProcess();


        for(final Channel channel:channels) {

            String[] labels = Interest_Point_Registration.getAllInterestPointLabelsForChannel(data, viewIds, channel);
            stepsToDo+=channelMultiplier*labels.length;

            for (final ViewId viewId : viewIds) {
                final ViewDescription vd = data.getSequenceDescription().getViewDescription(viewId);

                //same channel?
                if ( !vd.isPresent() || vd.getViewSetup().getChannel().getId() != channel.getId() )
                    continue;

                final ViewInterestPointLists vipl = vip.getViewInterestPointLists(viewId);

                for (String label:labels)
                {


                    InterestPointList interestPointList = vipl.getInterestPointList(label);
                    interestPointList.loadInterestPoints();
                    int interestPointCount =interestPointList.getInterestPoints().size();

                    //now do something reasonable with this count, maybe check against some upper threshold
                    if (interestPointCount>UPPER_THRESHOLD)
                    {
                        IJ.log("interest point count > "+UPPER_THRESHOLD+" at ch: "+channel.getName()+" angle: "+vd.getViewSetup().getAngle().getName()+" label :" +label+": "+interestPointCount);

                        isReasonable = false;
                    }

                    if (interestPointCount == ZERO)
                    {
                        IJ.log("interest point count = "+ZERO+" at ch: "+channel.getName()+" angle: "+vd.getViewSetup().getAngle().getName()+" label :" +label+": "+interestPointCount);
                        isReasonable = false;
                    }

                    //notify the user, it's not all the time accurate, but at least it's some feedback
                    IJ.showProgress(currentStep,stepsToDo);
                    currentStep++;
//                    System.out.println(channel.getName()+" "+vd.getViewSetup().getAngle().getName()+" "+label+": "+interestPointCount);
                }
            }
        }
        return isReasonable;
    }

    private static int maxNumberOfRegistrations(String xmlFileName)
    {
        int maxNumber =0;
        LoadParseQueryXML result = new LoadParseQueryXML();
        result.tryParsing(xmlFileName,false);
        result.queryDetails();

        SpimData2 data = result.getData();
        ViewRegistrations registrations = data.getViewRegistrations();
        final List< ViewId > viewIds = SpimData2.getAllViewIdsSorted(data, result.getViewSetupsToProcess(), result.getTimePointsToProcess());

        for(ViewId viewId:viewIds) {
            ViewRegistration viewRegistration = registrations.getViewRegistration(viewId);
            int transformListSize =viewRegistration.getTransformList().size();
            IJ.log(viewId.toString() +" "+transformListSize);
            if (transformListSize>maxNumber)
                maxNumber=transformListSize;
        }
        return maxNumber;
    }

    public static boolean hasMoreThanOneRegistration(String xmlFileName)
    {
        return maxNumberOfRegistrations(xmlFileName)>1;
    }

    public static boolean isDetectionReasonable(String xmlFileName)
    {
        LoadParseQueryXML result = new LoadParseQueryXML();
        result.tryParsing(xmlFileName,false);
        result.queryDetails();
        return isDetectionReasonable(result);
    }

    public static void main( String[] args )
    {
        new ImageJ();
//        new Detection_Verfication().run( null );
        isDetectionReasonable("/Volumes/wormspim2-1/worm7/worm7/dataset_thinoutRegister.xml");
        maxNumberOfRegistrations("/Volumes/wormspim2-1/worm7/worm7/dataset_thinoutRegister.xml");
        System.out.println("Done.");
    }

}
