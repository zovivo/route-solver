/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.giggsoff.jspritproj.utils;

import com.graphhopper.GHResponse;
import com.graphhopper.jsprit.analysis.toolbox.GraphStreamViewer;
import com.graphhopper.jsprit.analysis.toolbox.Plotter;
import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.job.Delivery;
import com.graphhopper.jsprit.core.problem.job.Pickup;
import com.graphhopper.jsprit.core.problem.solution.SolutionCostCalculator;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleType;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.reporting.SolutionPrinter;
import com.graphhopper.jsprit.core.util.Solutions;
import com.graphhopper.jsprit.io.problem.VrpXMLWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.giggsoff.jspritproj.alg.VehicleRoutingAlgorithmBuilder;
import org.giggsoff.jspritproj.models.Dump;
import org.giggsoff.jspritproj.models.Point;
import org.giggsoff.jspritproj.models.SGB;
import org.giggsoff.jspritproj.models.Truck;

/**
 *
 * @author giggsoff
 */
public class Solver {

    public static Map<String, Map<String, GHResponse>> hashMap = new HashMap<>();
    
    public static GHResponse getRoute(Point p1, Point p2, GraphhopperWorker gw){
        if (hashMap.containsKey(p1.toString())) {
                        if (hashMap.get(p1.toString()).containsKey(p2.toString())) {
                            return hashMap.get(p1.toString()).get(p2.toString());
                        } else {
                            GHResponse resp = gw.getRoute(p1.y, p1.x, p2.y, p2.x);
                            if (resp != null) {
                                hashMap.get(p1.toString()).put(p2.toString(), resp);
                                return resp;
                            }
                        }
                    } else {
                        GHResponse resp = gw.getRoute(p1.y, p1.x, p2.y, p2.x);
                        if (resp != null) {
                            hashMap.put(p1.toString(), new HashMap<>());
                            hashMap.get(p1.toString()).put(p2.toString(), resp);
                        }
                    }
        return null;
    }

    public static VehicleRoutingProblemSolution solve(List<Truck> trList, List<SGB> sgbList, List<Dump> dumpList, GraphhopperWorker gw, boolean showPlot) {

        System.out.println("INITIAL COUNT: " + gw.ghCount);
        /*
         * get a vehicle type-builder and build a type with the typeId "vehicleType" and one capacity dimension, i.e. weight, and capacity dimension value of 2
         */
        final int WEIGHT_INDEX = 0;
        final double max_speed_mps = 3.6*50;
        VehicleTypeImpl.Builder vehicleTypeBuilder = VehicleTypeImpl.Builder.newInstance("vehicleType").addCapacityDimension(WEIGHT_INDEX, 100).setMaxVelocity(max_speed_mps).setCostPerServiceTime(10);
        VehicleType vehicleType = vehicleTypeBuilder.build();

        VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance();
        /*
         * get a vehicle-builder and build a vehicle located at (10,10) with type "vehicleType"
         */
        for (Truck tr : trList) {
            VehicleImpl.Builder vehicleBuilder = VehicleImpl.Builder.newInstance(tr.id);
            vehicleBuilder.setStartLocation(Location.newInstance(tr.coord.x, tr.coord.y));
            vehicleBuilder.setType(vehicleType);
            vehicleBuilder.setReturnToDepot(false);
            VehicleImpl vehicle = vehicleBuilder.build();
            vrpBuilder.addVehicle(vehicle);
        }

        /*
         * build services at the required locations, each with a capacity-demand of 1.
         */
        int i = 0;
        for (SGB sgb : sgbList) {
            Pickup service = Pickup.Builder.newInstance(Integer.toString(++i)).setServiceTime(100).addSizeDimension(WEIGHT_INDEX, 20).setLocation(Location.newInstance(sgb.coord.x, sgb.coord.y)).build();
            vrpBuilder.addJob(service);
        }
        
        for (Dump dump : dumpList) {
            Delivery service = Delivery.Builder.newInstance(Integer.toString(++i)).setServiceTime(100).addSizeDimension(WEIGHT_INDEX, 10000).setLocation(Location.newInstance(dump.coord.x, dump.coord.y)).build();
            vrpBuilder.addJob(service);
        }

        SolutionCostCalculator costCalculator = (VehicleRoutingProblemSolution solution) -> {
            double costs = 0.;
            List<VehicleRoute> routes = (List<VehicleRoute>) solution.getRoutes();
            for (VehicleRoute route : routes) {
                List<Location> lc = new ArrayList<>();
                lc.add(route.getStart().getLocation());
                for (TourActivity ta : route.getActivities()) {
                    lc.add(ta.getLocation());
                }
                lc.add(route.getEnd().getLocation());
                for (int i1 = 0; i1 < lc.size() - 1; i1++) {
                    GHResponse grp = getRoute(new Point(lc.get(i1).getCoordinate()), new Point(lc.get(i1+1).getCoordinate()), gw);
                    if(grp!=null){
                        costs += grp.getBest().getDistance();
                    }
                }
                //costs += route.getVehicle().getType().getVehicleCostParams().fix;
                /*costs+=stateManager.getRouteState(route, InternalStates.COSTS, Double.class);
                for (RewardAndPenaltiesThroughSoftConstraints contrib : contribs) {
                costs+=contrib.getCosts(route);
                }*/
            }
            return costs;
        };
        
        vrpBuilder.setFleetSize(VehicleRoutingProblem.FleetSize.FINITE);

        VehicleRoutingProblem problem = vrpBuilder.build();

        /*
         * get the algorithm out-of-the-box.
         */
        VehicleRoutingAlgorithmBuilder vraBuilder = new VehicleRoutingAlgorithmBuilder(problem, "algorithmConfig.xml");
        vraBuilder.addDefaultCostCalculators();
        vraBuilder.setObjectiveFunction(costCalculator);
        VehicleRoutingAlgorithm algorithm = vraBuilder.build();

        /*
         * and search a solution
         */
        Collection<VehicleRoutingProblemSolution> solutions = algorithm.searchSolutions();

        /*
         * get the best
         */
        VehicleRoutingProblemSolution bestSolution = Solutions.bestOf(solutions);

        if (showPlot) {

            new VrpXMLWriter(problem, solutions).write("output/problem-with-solution.xml");

            SolutionPrinter.print(problem, bestSolution, SolutionPrinter.Print.VERBOSE);

            /*
         * plot
             */
            new Plotter(problem, bestSolution).plot("output/plot.png", "simple example");

            /*
        render problem and solution with GraphStream
             */
            new GraphStreamViewer(problem, bestSolution).labelWith(GraphStreamViewer.Label.ID).setRenderDelay(200).display();

        }

        System.out.println("LAST COUNT: " + gw.ghCount);
        
        return bestSolution;
    }
}
