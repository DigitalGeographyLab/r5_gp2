package com.conveyal.r5.customcost;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.conveyal.r5.OneOriginResult;
import com.conveyal.r5.analyst.TravelTimeComputer;
import com.conveyal.r5.analyst.cluster.AnalysisWorkerTask;
import com.conveyal.r5.analyst.network.GridLayout;
import com.conveyal.r5.rastercost.CustomCostField;
import com.conveyal.r5.rastercost.CustomCostFieldException;
import com.conveyal.r5.streets.EdgeStore;
import com.conveyal.r5.streets.ReverseRoutingTest;
import com.conveyal.r5.transit.TransportNetwork;

import static com.conveyal.r5.analyst.network.SimpsonDesertTests.SIMPSON_DESERT_CORNER;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/* GP2 edit: added tests for custom cost logic */
public class CustomCostTest {
    private static final Logger LOG = LoggerFactory.getLogger(ReverseRoutingTest.class);

    HashMap<Long, Double> customCostHashMap;

    @BeforeEach
    public void setUp () throws Exception {
        customCostHashMap = generateCustomCostHashMap();
    }

    public static HashMap<Long, Double> generateCustomCostHashMap() {
        HashMap<Long, Double> osmIdMap = new HashMap<>();
        // put a known value as the first in the hashmap
        osmIdMap.put(123123L, 7.0);
        Random rand = new Random();
        for (int i = 0; i < 10; i++) {
            long osmId = i;
            double randomValue = rand.nextDouble();

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
        HashMap<Long, Double> emptyHashmap = new HashMap<>();
        // assertThrows(IllegalArgumentException.class, );
        assertThrows(IllegalArgumentException.class, () -> new CustomCostField("testKey", 3, emptyHashmap));
        assertThrows(IllegalArgumentException.class, () -> new CustomCostField("testKey", 3, null));
    }

    /**
     * Got the grid layout and the test from SimpsonDesertTests.java
     */
    @Test
    public void testRoutingWithCustomCosts() { 
        // this test could be made more robust by:
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

        assertTrue(uniqueOsmIds.size() > 0);

        HashMap<Long, Double> customCostHashMap = new HashMap<>();
        // add the acquired osmIds to a hashmap with random values
        for (Long osmId : uniqueOsmIds) {
            // add just a small increate in traveltime for not 
            // making the vertices unreachable
            customCostHashMap.put(osmId, 0.25);
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
        assertTrue(oneOriginResult.osmIdResults.size() > 0);
        assertTrue(oneOriginResult.travelTimes.nPoints > 0);

        // test that the routing result osmId has only valid osmIds which are found from the network
        // this is important, that atleast some values have the same osmids -> they will be added custom costs
        boolean hasOnlyValidOsmIdsFoundFromNetwork = oneOriginResult.osmIdResults.stream()
            .allMatch(innerList -> innerList.stream().allMatch(uniqueOsmIds::contains));
        assertTrue(hasOnlyValidOsmIdsFoundFromNetwork);

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

        assertTrue(oneOriginResultNoCustomCosts != null);
        // check that no osmIdResults found for they are not created when there are no customCosts
        assertTrue(oneOriginResultNoCustomCosts.osmIdResults == null);
        assertTrue(oneOriginResultNoCustomCosts.travelTimes.nPoints > 0);

        int [][] travelTimeValuesNoCustomCost = oneOriginResultNoCustomCosts.travelTimes.getValues();

        List<Integer> DefaultRoutingNoCustomCosts = Arrays.stream(travelTimeValuesNoCustomCost)
                           .flatMapToInt(Arrays::stream)
                           .boxed()
                           .collect(Collectors.toList());

        // assert before and after removing unreachable vertices values
        assertTrue(CustomCostTravelTimes.size() == DefaultRoutingNoCustomCosts.size());
        assertTrue(CustomCostTravelTimes.size() > 0);
        assertTrue(CustomCostTravelTimes.size() == DefaultRoutingNoCustomCosts.size());
        assertTrue(!CustomCostTravelTimes.equals(DefaultRoutingNoCustomCosts));

        boolean allValuesIncreasedOrSame = IntStream.range(0, CustomCostTravelTimes.size())
        .allMatch(i -> CustomCostTravelTimes.get(i) >= DefaultRoutingNoCustomCosts.get(i));

        // check that customCost value is always same or bigger, never smaller
        assertTrue(allValuesIncreasedOrSame);
    }

     @Test
    public void testInvalidCustomCostMapOsmIdNotFound() { 
        GridLayout gridLayout = new GridLayout(SIMPSON_DESERT_CORNER, 6);
        TransportNetwork Network = gridLayout.generateNetwork();
        CustomCostField customCostInstance = new CustomCostField("testKey", 1.5, customCostHashMap);
        Network.streetLayer.edgeStore.costFields = CustomCostField.wrapToEdgeStoreCostFieldsList(customCostInstance);

        // build the task from the grid, example taken from SimpsonDesertTests.java
        AnalysisWorkerTask task = gridLayout.newTaskBuilder()
                .setOrigin(2, 2)
                .setDestination(5, 3)
                .uniformOpportunityDensity(2)
                .monteCarloDraws(1)
                .build();

        TravelTimeComputer computer = new TravelTimeComputer(task, Network);

        // make sure that invalid customCostMap throws CustomCostFieldException
        // error happens in additionalTraversalTimeSeconds -> Double customCostFactor = this.customCostMap.get(keyOsmId);
        Exception exception = assertThrows(CustomCostFieldException.class, () -> {
            computer.computeTravelTimes();
        });
        
        assertTrue(exception.getMessage().contains("Custom cost not found for edge with osmId:"));
    }
}