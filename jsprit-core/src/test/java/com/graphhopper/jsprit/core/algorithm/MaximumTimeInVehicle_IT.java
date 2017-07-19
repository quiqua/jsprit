package com.graphhopper.jsprit.core.algorithm;

import com.graphhopper.jsprit.core.algorithm.box.Jsprit;
import com.graphhopper.jsprit.core.algorithm.state.StateManager;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.constraint.ConstraintManager;
import com.graphhopper.jsprit.core.problem.constraint.MaximumTimeInVehicleConstraint;
import com.graphhopper.jsprit.core.problem.job.Delivery;
import com.graphhopper.jsprit.core.problem.job.Shipment;
import com.graphhopper.jsprit.core.problem.solution.SolutionCostCalculator;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.reporting.SolutionPrinter;
import com.graphhopper.jsprit.core.util.Coordinate;
import com.graphhopper.jsprit.core.util.Solutions;
import com.graphhopper.jsprit.core.util.VehicleRoutingTransportCostsMatrix;
import org.junit.Assert;
import org.junit.Test;

public class MaximumTimeInVehicle_IT {

    VehicleRoutingProblem vrp;
    VehicleRoutingTransportCostsMatrix transportCosts;
    Shipment s1;
    Shipment newShipment;

    SolutionCostCalculator objectiveFunction = new SolutionCostCalculator() {
        @Override
        public double getCosts(VehicleRoutingProblemSolution solution) {
            double costs = 0.;
            for (VehicleRoute route : solution.getRoutes()) {
                TourActivity previousActivity = route.getStart();
                for (TourActivity activity : route.getActivities()) {
                    costs += vrp.getTransportCosts()
                        .getTransportTime(previousActivity.getLocation(), activity.getLocation(), previousActivity.getEndTime(), route.getDriver(), route.getVehicle());
                    previousActivity = activity;
                }
                costs += vrp.getTransportCosts()
                    .getTransportTime(previousActivity.getLocation(), route.getEnd().getLocation(), previousActivity.getEndTime(), route.getDriver(), route.getVehicle());
            }
            return costs;
        }
    };

    public void init() {
        Delivery d1 = Delivery.Builder.newInstance("d1")
            .setLocation(Location.Builder.newInstance()
                .setCoordinate(Coordinate.newInstance(13.349671, 52.496692))
                .setId("d1:dropoff")
                .build())
            .addTimeWindow(1.477695882E12, 1.4776966914E12)
            .addSizeDimension(0, 1)
            .build();

        s1 = Shipment.Builder.newInstance("s1")
            .setPickupLocation(Location.Builder.newInstance()
                .setCoordinate(Coordinate.newInstance(13.3333032,52.5023679))
                .setId("s1:pickup")
                .build())
            .addPickupTimeWindow(1.477696619E12, Double.MAX_VALUE)
            .setDeliveryLocation(Location.Builder.newInstance()
                .setCoordinate(Coordinate.newInstance(13.4029296,52.5298737))
                .setId("s1:dropoff")
                .build())
            .addDeliveryTimeWindow(1.477695882E12, 1.47769883354E12)
            .addSizeDimension(0, 2)
            .build();

        newShipment = Shipment.Builder.newInstance("newShipment")
            .setPickupLocation(Location.Builder.newInstance()
                .setCoordinate(Coordinate.newInstance(13.339671,52.496692))
                .setId("newShipment:pickup")
                .build())
            .addPickupTimeWindow(1.47769584E12, 1.47769764E12)
            .setDeliveryLocation(Location.Builder.newInstance()
                .setCoordinate(Coordinate.newInstance(13.3363032,52.513679))
                .setId("newShipment:dropoff")
                .build())
            //.addDeliveryTimeWindow(0.0, Double.MAX_VALUE) no time window required, insertion will be determined by constraint
            .addSizeDimension(0, 1)
            .build();

        VehicleTypeImpl t = VehicleTypeImpl.Builder.newInstance("capacity-5").addCapacityDimension(0, 5).build();
        VehicleImpl v1 = VehicleImpl.Builder.newInstance("v1")
            .setStartLocation(Location.Builder.newInstance()
                .setCoordinate(Coordinate.newInstance(13.3513326,52.4987905))
                .setId("v1:location")
                .build())
            .setReturnToDepot(false)
            .setEarliestStart(1.477695882E12)
            .setType(t)
            .build();

        transportCosts = VehicleRoutingTransportCostsMatrix.Builder.newInstance(true)
            .addTransportTime("v1:location", "s1:pickup", 458600.0)
            .addTransportTime("v1:location", "newShipment:pickup", 312400.0)
            .addTransportTime("v1:location", "newShipment:dropoff", 638600.0)
            .addTransportTime("v1:location", "d1:dropoff", 96600.0)
            .addTransportTime("v1:location", "s1:dropoff", 1555200.0)
            .addTransportTime("d1:pickup", "s1:pickup", 457200.0)
            .addTransportTime("d1:pickup", "newShipment:pickup", 580400.0)
            .addTransportTime("d1:pickup", "newShipment:dropoff", 608800.0)
            .addTransportTime("d1:pickup", "d1:dropoff", 752400.0)
            .addTransportTime("d1:pickup", "s1:dropoff", 1866600.0)
            .addTransportTime("s1:pickup", "s1:pickup", 0.0)
            .addTransportTime("s1:pickup", "newShipment:pickup", 322000.0)
            .addTransportTime("s1:pickup", "newShipment:dropoff", 489200.0)
            .addTransportTime("s1:pickup", "d1:dropoff", 455000.0)
            .addTransportTime("s1:pickup", "s1:dropoff", 1640400.0)
            .addTransportTime("newShipment:pickup", "s1:pickup", 330800.0)
            .addTransportTime("newShipment:pickup", "newShipment:pickup", 0.0)
            .addTransportTime("newShipment:pickup", "newShipment:dropoff", 757000.0)
            .addTransportTime("newShipment:pickup", "d1:dropoff", 333600.0)
            .addTransportTime("newShipment:pickup", "s1:dropoff", 1665800.0)
            .addTransportTime("newShipment:dropoff", "s1:pickup", 556000.0)
            .addTransportTime("newShipment:dropoff", "newShipment:dropoff", 0.0)
            .addTransportTime("newShipment:dropoff", "d1:dropoff", 726200.0)
            .addTransportTime("newShipment:dropoff", "s1:dropoff", 1367800.0)
            .addTransportTime("d1:dropoff", "s1:pickup", 466200.0)
            .addTransportTime("d1:dropoff", "newShipment:pickup", 324000.0)
            .addTransportTime("d1:dropoff", "newShipment:dropoff", 732200.0)
            .addTransportTime("d1:dropoff", "d1:dropoff", 0.0)
            .addTransportTime("d1:dropoff", "s1:dropoff", 1648800.0)
            .addTransportTime("s1:dropoff", "newShipment:pickup", 1497600.0)
            .addTransportTime("s1:dropoff", "newShipment:dropoff", 1144400.0)
            .addTransportTime("s1:dropoff", "d1:dropoff", 1484400.0)
            .addTransportTime("s1:dropoff", "s1:dropoff", 0.0)
            .build();

        vrp = VehicleRoutingProblem.Builder.newInstance()
            .setFleetSize(VehicleRoutingProblem.FleetSize.FINITE)
            .setRoutingCost(transportCosts)
            .addVehicle(v1).addJob(d1).addJob(s1).addJob(newShipment)
            .build();
    }

    @Test
    public void travelTime_cannot_be_fulfilled_and_exclude_newShipment(){
        init();

        StateManager stateManager = new StateManager(vrp);
        ConstraintManager constraintManager = new ConstraintManager(vrp,stateManager);
        constraintManager.addConstraint(new MaximumTimeInVehicleConstraint(50000.0, transportCosts, stateManager, "newShipment"), ConstraintManager.Priority.CRITICAL);

        VehicleRoutingAlgorithm vra = Jsprit.Builder.newInstance(vrp)
            .setStateAndConstraintManager(stateManager,constraintManager)
            .setObjectiveFunction(objectiveFunction)
            .buildAlgorithm();
        VehicleRoutingProblemSolution solution = Solutions.bestOf(vra.searchSolutions());

        SolutionPrinter.print(vrp,solution, SolutionPrinter.Print.VERBOSE);

        Assert.assertEquals(1, solution.getUnassignedJobs().size());
        Assert.assertEquals(true, solution.getUnassignedJobs().contains(newShipment));
    }

    @Test
    public void travelTime_can_be_fulfilled_and_exclude_existing_shipment_s1(){
        init();

        StateManager stateManager = new StateManager(vrp);
        ConstraintManager constraintManager = new ConstraintManager(vrp,stateManager);
        constraintManager.addConstraint(new MaximumTimeInVehicleConstraint(800000.0, transportCosts, stateManager, "newShipment"), ConstraintManager.Priority.CRITICAL);

        VehicleRoutingAlgorithm vra = Jsprit.Builder.newInstance(vrp)
            .setStateAndConstraintManager(stateManager,constraintManager)
            .setObjectiveFunction(objectiveFunction)
            .buildAlgorithm();
        VehicleRoutingProblemSolution solution = Solutions.bestOf(vra.searchSolutions());

        SolutionPrinter.print(vrp,solution, SolutionPrinter.Print.VERBOSE);

        Assert.assertEquals(1, solution.getUnassignedJobs().size());
        Assert.assertEquals(true, solution.getUnassignedJobs().contains(s1));
    }

    @Test
    public void travelTime_can_be_fulfilled_and_include_all_jobs(){
        init();
        StateManager stateManager = new StateManager(vrp);
        ConstraintManager constraintManager = new ConstraintManager(vrp,stateManager);
        constraintManager.addConstraint(new MaximumTimeInVehicleConstraint(1000000.0, transportCosts, stateManager, "newShipment"), ConstraintManager.Priority.CRITICAL);

        VehicleRoutingAlgorithm vra = Jsprit.Builder.newInstance(vrp)
            .setStateAndConstraintManager(stateManager,constraintManager)
            .setObjectiveFunction(objectiveFunction)
            .buildAlgorithm();
        VehicleRoutingProblemSolution solution = Solutions.bestOf(vra.searchSolutions());

        SolutionPrinter.print(vrp,solution, SolutionPrinter.Print.VERBOSE);

        Assert.assertEquals(0, solution.getUnassignedJobs().size());
    }
}
