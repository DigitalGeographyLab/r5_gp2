package com.conveyal.r5;

import java.util.List;

import org.checkerframework.checker.units.qual.t;

import com.conveyal.r5.analyst.AccessibilityResult;
import com.conveyal.r5.analyst.TemporalDensityResult;
import com.conveyal.r5.analyst.cluster.PathResult;
import com.conveyal.r5.analyst.cluster.TravelTimeResult;

/**
 * This provides a single return type (for internal R5 use) for all the kinds of results we can get from a travel time
 * computer and reducer for a single origin point. Currently, these results include travel times to points in a
 * destination pointset, and accessibility indicator values for various travel time cutoffs and percentiles of travel
 * time.
 *
 * TODO add fields to record travel time breakdowns into wait and ride and walk time.
 */
public class OneOriginResult {

    public final TravelTimeResult travelTimes;

    public final AccessibilityResult accessibility;

    public final PathResult paths;

    public final TemporalDensityResult density;

    /* GP2 edit: add this attribute to save OsmIdResults */
    public final List<List<Long>> osmIdResults;

    /* GP2 edit: add optional OsmIdResults */
    public OneOriginResult(TravelTimeResult travelTimes, AccessibilityResult accessibility, PathResult paths, TemporalDensityResult density) {
        this(travelTimes, accessibility, paths, density, null);
    }

    /* GP2 edit: add optional OsmIdResults 
    *  edit2: add density with r5 version update
    *  edit3: use only single constructor including density, added osmIdResults as null by default where calling this constructor
    */  
    public OneOriginResult(TravelTimeResult travelTimes, AccessibilityResult accessibility, PathResult paths, TemporalDensityResult density, List<List<Long>> osmIdResults) {
        this.travelTimes = travelTimes;
        this.accessibility = accessibility;
        this.paths = paths;
        this.density = density;
        this.osmIdResults = osmIdResults;
    }
}
