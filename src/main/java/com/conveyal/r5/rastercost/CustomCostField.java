package com.conveyal.r5.rastercost;
import com.conveyal.r5.streets.EdgeStore;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * CustomCost for enabling Greenpaths2 flexible custom costs
 * This class should be utilized in r5py in the following way:
 * 
 * 1) initialize/calculate the custom cost map (e.g. from raster data)
 * which is a dict of {osmid: value} in seconds added to the base traversal time.
 * Notice that the python dict type of custom cost map needs to be converted to Java HashMap
 * 
 * 2) initialize the CustomCostField with the custom cost map and sensitivity coefficient (default 1.0)
 * ! currently the sensitivity coefficient can also be a negative number
 * but the sum of basetraversaltime + all cost fields cant be less than 1 
 * 
 * 3) add list customCost instances to the TransportNetwork.
 * List because EdgeStore's costFields is a list of CostField instances
 * also this way we can have multiple custom cost fields if wanted
 * 
 * 4) run the routing process. In the routing logic each edge will be checked for the custom cost as:
*      - go throught each edge to see how long it takes to traverse
 *     - use MultistageTraversalTimeCalculator if EdgeStore has costFields
 *     - when MultistageTraversalTimeCalculator is used, routing will add a custom cost on top of the 
 *       base traversal time for each edge. This cost is calculated using this classes additionalTraversalTimeSeconds.
 *       This method will get the osmId of the current edge and use it to get the custom cost from the custom cost hashmap
 *       using the osmid as the key. If no custom cost is found, 0 is used as default
 *       for custom cost addition, which is equivalent to no custom cost.
 * 
 *      !Note: this process is adding the custom costs as seconds on top of the base traversal time. So e.g. in matrix
 *      traveltimes the times include the additional "cost" which in this context is seconds. So actually the 
 *      custom costs are seconds. The amount of additional seconds has to be separately calculated
 *      in a "preprocessing" phase which produces the custom cost map.
 * 
 * Created by roope on 11.10.2023.
 */

/* GP2 edit: create this class to be used in Greenpaths custom cost exposure routing */
public class CustomCostField implements CostField, Serializable {

    // custom cost map has osmId as the key and additional seconds as value
    public HashMap<Long, Double> customCostMap = null;

    // define the sensitivity coefficient for the custom cost field
    // currently also supports negative number
    // this is needed for deriving multiple routes with different weights to custom cost
    public double sensitivityCoefficient = 1;

    /** 
    * displayKey currently not really used, implemented due to CostField interface
    *
    * this could be used for displaying the custom cost field name e.g.
    * if we use multiple custom cost fields, add the names to the return values
    * which could in python side be added to the result df to see which custom cost fields were used
    */
    private final String displayKey;


/**
 * 
 * @param displayKey
 * displayKey currently not really used, implemented due to CostField interface
 * @param sensitivityCoefficient
 * sensitivityCoefficient can currently also be a negative number
 * the only restricting factor is that the routing logic in MultistageTraversalTimeCalculator
 * will fallback to a positive number 1 if the sum of basetraversaltime + all custom cost found for that edge
 * will be less than 1. This is because the routing logic will not allow negative traversal times.
 * @param customCostMap
 * customCostMap has osmId as the key and the custom cost seconds as the value
 */
    public CustomCostField (String displayKey, double sensitivityCoefficient, HashMap<Long, Double> customCostMap) {
        validateCustomCostMap(customCostMap);
        this.sensitivityCoefficient = sensitivityCoefficient;
        this.displayKey = displayKey;
    }

    /**
     * Check that the custom cost map is not empty, do we need more validation?
     */
    private void validateCustomCostMap(HashMap<Long, Double> customCostMap) {
        if (customCostMap == null || customCostMap.isEmpty()) {
            throw new IllegalArgumentException("Custom cost map cant be empty when initializing CustomCostField");
        }
        this.customCostMap = customCostMap;
    }

    /**
     * Override method for adding custom cost seconds to the base traversal time
     * 
     * This approach of adding custom cost seconds on top of the base traversal time
     * makes the travel times not actual second times but rather "cost" times
     */
    @Override
    public int additionalTraversalTimeSeconds (EdgeStore.Edge currentEdge, int baseTraversalTimeSeconds) {
        // get the <long> osmId of the current edge
        long edgeOsmId = currentEdge.getOSMID();
        // get the custom cost factor from the custom cost map using the edgeas osmId as key
        Long keyOsmId = Long.valueOf(edgeOsmId);
        // using get (not getOrDefault) so this will throw an error if no custom cost is found
        // thus leaving the responsibility to preprocessing module to make sure all edges have custom cost
        Double customCostFactor = this.customCostMap.get(keyOsmId);
        // throw an error if no custom cost is found
        if (customCostFactor == null) {
            throw new CustomCostFieldException("Custom cost not found for edge with osmId: " + currentEdge.getOSMID());
        }
        // calculate seconds to be added to the base traversal time 
        // multiply the base travel time with custom cost factor and sensitivity coefficient
        // this value is then added to the base traversal time
        return (int) Math.round(baseTraversalTimeSeconds * customCostFactor * this.sensitivityCoefficient);
    }

    // currently not really used, implemented due to CostField interface
    @Override
    public String getDisplayKey () {
        return this.displayKey;
    }

    // currently not really used, implemented due to CostField interface
    @Override
    public double getDisplayValue (int osmIdKey) {
        // will need this conversation because implementation needs int but key is long
        Long osmIdKeyLong = Long.valueOf(osmIdKey);
        return this.customCostMap.get(osmIdKeyLong);
    }

    // convert 1-n custom cost fields to a list of custom cost fields
    public static List<CostField> wrapToEdgeStoreCostFieldsList(CostField... customCostFields) {
        return new ArrayList<>(Arrays.asList(customCostFields));
    }

}

