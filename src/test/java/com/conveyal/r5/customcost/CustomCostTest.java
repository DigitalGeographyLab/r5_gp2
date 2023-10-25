package com.conveyal.r5.customcost;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.conveyal.r5.OneOriginResult;
import com.conveyal.r5.analyst.TravelTimeComputer;
import com.conveyal.r5.analyst.cluster.AnalysisWorkerTask;
import com.conveyal.r5.analyst.network.GridLayout;
import com.conveyal.r5.rastercost.CustomCostField;
import com.conveyal.r5.streets.EdgeStore;
import com.conveyal.r5.streets.ReverseRoutingTest;
import com.conveyal.r5.transit.TransportNetwork;

import static com.conveyal.r5.analyst.network.SimpsonDesertTests.SIMPSON_DESERT_CORNER;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


/* GP2 edit: added tests for custom cost logic */
public class CustomCostTest {
    private static final Logger LOG = LoggerFactory.getLogger(ReverseRoutingTest.class);

    HashMap<Long, Integer> customCostHashMap;

    @BeforeEach
    public void setUp () throws Exception {
        customCostHashMap = generateCustomCostHashMap();
    }

    public static HashMap<Long, Integer> generateCustomCostHashMap() {
        HashMap<Long, Integer> osmIdMap = new HashMap<>();
        // put a known value as the first in the hashmap
        osmIdMap.put(123123L, 7);
        Random rand = new Random();
        for (int i = 0; i < 2; i++) {
            long osmId = i;
            int randomValue = rand.nextInt(11);

            osmIdMap.put(osmId, randomValue);
        }

        return osmIdMap;
    }

    @Test
    public void testCreateValidCustomCostField () {
        CustomCostField customCostInstance = new CustomCostField("testKey", 3, customCostHashMap);
        double targetValue = customCostHashMap.get(123123L);
        assertEquals(customCostInstance.getDisplayKey(), "testKey");
        assertEquals(targetValue, 7.0);
        assertEquals(customCostHashMap.size() > 0, true);
    }

    @Test
    public void testCreateInvalidCustomCostField () {
        HashMap<Long, Integer> emptyHashmap = new HashMap<>();
        // assertThrows(IllegalArgumentException.class, );
        assertThrows(IllegalArgumentException.class, () -> new CustomCostField("testKey", 3, emptyHashmap));
        assertThrows(IllegalArgumentException.class, () -> new CustomCostField("testKey", 3, null));
    }

    /**
     * Got the grid layout and the test from SimpsonDesertTests.java
     */
    @Test
    public void testRoutingWithCustomCosts() {
        // edit: for some reason not all the traveltimes e.g. OD pointpairs aren't finding any osmIds
        // that is why we cant directly compare them in the end as arrays
        // this can also be because of how the destination pointSet is created/handled?
        // we can compare that all the routings with custom costs have bigger or equal traveltimes
        // and that the amouunt of longer traveltimes is equal to the amount of osmIdResults (lists) found
        // for one list is presenting one traveltimes paths osmIds

        // this test could be made better
        // 1. use different methods of creating the network and the task
        // which would let us see that the times have increased the amount we set
        // the custom cost per osmId to it

        // currently this test is sufficient, it shows that adding custom costs to the network
        // are changing the correct amount of travel times and that the custom cost components are working

        GridLayout gridLayout = new GridLayout(SIMPSON_DESERT_CORNER, 6);
        TransportNetwork Network = gridLayout.generateNetwork();
        // take all the osmIds that the network has
        // add them to a list and make sure that they exist
        // notice: for some reason this logic doesn't find all the osmIds
        List<Long> osmIds = new ArrayList<>();
        EdgeStore.Edge e = Network.streetLayer.edgeStore.getCursor(0);
        do {
            osmIds.add(e.getOSMID());
        } while (e.advance());

        final List<Long> uniqueOsmIds = osmIds.stream().distinct().collect(Collectors.toList());

        assert(uniqueOsmIds.size() > 0);

        HashMap<Long, Integer> customCostHashMap = new HashMap<>();
        // add the acquired osmIds to a hashmap with random values
        for (Long osmId : uniqueOsmIds) {
            // add just a small increate in traveltime for not 
            // making the vertices unreachable
            customCostHashMap.put(osmId, 2);
        }

        // add the hashmap as the customCostHashMap to the customCostInstance
        CustomCostField customCostInstance = new CustomCostField("testKey", 2, customCostHashMap);
        Network.streetLayer.edgeStore.costFields = CustomCostField.wrapToEdgeStoreCostFieldsList(customCostInstance);

        // build the task from the grid, example taken from SimpsonDesertTests.java
        AnalysisWorkerTask task = gridLayout.newTaskBuilder()
                .setOrigin(2, 2)
                .setDestination(5, 3)
                .uniformOpportunityDensity(2)
                .monteCarloDraws(1)
                .build();

        TravelTimeComputer computer = new TravelTimeComputer(task, Network);
        OneOriginResult oneOriginResult = computer.computeTravelTimes();

        assert(oneOriginResult != null);
        assert(oneOriginResult.osmIdResults.size() > 0);
        assert(oneOriginResult.travelTimes.nPoints > 0);

        // test that the routing result osmId has only valid osmIds which are found from the network
        // this is important, that atleast some values have the same osmids -> they will be added custom costs
        boolean hasOnlyValidOsmIdsFoundFromNetwork = oneOriginResult.osmIdResults.stream()
            .allMatch(innerList -> innerList.stream().allMatch(uniqueOsmIds::contains));
        assert(hasOnlyValidOsmIdsFoundFromNetwork);

        int [][] travelTimeValues = oneOriginResult.travelTimes.getValues();

        // flatten the two dimensional array to a list
        List<Integer> CustomCostTravelTimes = Arrays.stream(travelTimeValues)
                           .flatMapToInt(Arrays::stream)
                           .boxed()
                           .collect(Collectors.toList());


        // REMOVE COSTFIELDS FROM NETWORK AND RUN ROUTING FOR COMPARISON

        Network.streetLayer.edgeStore.costFields = null;
        TravelTimeComputer computerNoCustomCosts = new TravelTimeComputer(task, Network);
        OneOriginResult oneOriginResultNoCustomCosts = computerNoCustomCosts.computeTravelTimes();

        assert(oneOriginResultNoCustomCosts != null);
        // check that no osmIdResults found for they are not created when there are no customCosts
        assert(oneOriginResultNoCustomCosts.osmIdResults == null);
        assert(oneOriginResultNoCustomCosts.travelTimes.nPoints > 0);

        int [][] travelTimeValuesNoCustomCost = oneOriginResultNoCustomCosts.travelTimes.getValues();

        List<Integer> DefaultRoutingNoCustomCosts = Arrays.stream(travelTimeValuesNoCustomCost)
                           .flatMapToInt(Arrays::stream)
                           .boxed()
                           .collect(Collectors.toList());

        // assert before and after removing unreachable vertices values
        assert(CustomCostTravelTimes.size() == DefaultRoutingNoCustomCosts.size());
        // remove all the Integer.MAX_VALUE from both travelTimeLists
        // the Integer.MAX_VALUE (2147483647) is the value for unreachable vertices
        DefaultRoutingNoCustomCosts.removeAll(Collections.singleton(2147483647));
        CustomCostTravelTimes.removeAll(Collections.singleton(2147483647));
        assert(CustomCostTravelTimes.size() > 0);
        assert(CustomCostTravelTimes.size() == DefaultRoutingNoCustomCosts.size());
        assert(!CustomCostTravelTimes.equals(DefaultRoutingNoCustomCosts));

        Integer biggerTraveltimesCounter = 0;
        // check that customCost value is always same or bigger, never smaller
        // also count the amount of bigger (changed) travel times
        for (int i = 0; i < CustomCostTravelTimes.size(); i++) {
            assert(CustomCostTravelTimes.get(i) >= DefaultRoutingNoCustomCosts.get(i));
            if (CustomCostTravelTimes.get(i) > DefaultRoutingNoCustomCosts.get(i)) {
                biggerTraveltimesCounter++;
            }
        }

        // check that the amount of bigger travel times is the same as the amount of osmIdResults
        // one osmIdResult list has the osmIds for one destination points path
        // so as many traveltimes were bigger as many osmIdResults were found from the routing
        assert(biggerTraveltimesCounter == oneOriginResult.osmIdResults.size());
    }    
}