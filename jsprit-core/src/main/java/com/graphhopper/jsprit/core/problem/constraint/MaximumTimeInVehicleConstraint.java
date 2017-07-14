package com.graphhopper.jsprit.core.problem.constraint;

import com.graphhopper.jsprit.core.algorithm.state.StateId;
import com.graphhopper.jsprit.core.algorithm.state.StateManager;
import com.graphhopper.jsprit.core.problem.misc.JobInsertionContext;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;
import com.graphhopper.jsprit.core.util.VehicleRoutingTransportCostsMatrix;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class MaximumTimeInVehicleConstraint implements HardActivityConstraint {

    private final Double maximumAllowedTravelTime;
    private final VehicleRoutingTransportCostsMatrix transportCostsMatrix;
    private final StateManager stateManager;
    private final String jobId;
    private final StateId temporaryPickupActivityStateId;
    private final StateId temporaryNextActivityStateId;
    private final StateId plannedPreviousActivityStateId;
    private final StateId plannedNewActivityStateId;
    private final StateId plannedNextActivityStateId;

    public MaximumTimeInVehicleConstraint(Double maximumAllowedTravelTime, VehicleRoutingTransportCostsMatrix transportCostsMatrix, StateManager stateManager, String jobId){
        this.maximumAllowedTravelTime = maximumAllowedTravelTime;
        this.transportCostsMatrix = transportCostsMatrix;
        this.stateManager = stateManager;
        this.jobId = jobId;
        this.temporaryPickupActivityStateId = stateManager.createStateId("temporaryPickupActivity");
        this.temporaryNextActivityStateId = stateManager.createStateId("temporaryNextActivity");
        this.plannedPreviousActivityStateId = stateManager.createStateId("plannedPreviousActivity");
        this.plannedNewActivityStateId = stateManager.createStateId("plannedNewActivity");
        this.plannedNextActivityStateId = stateManager.createStateId("plannedNextActivity");
    }


    @Override
    public ConstraintsStatus fulfilled(JobInsertionContext iFacts, TourActivity prevAct, TourActivity newAct, TourActivity nextAct, double prevActDepTime) {
        VehicleRoute route = iFacts.getRoute();
        List<TourActivity> tourActivities = route.getActivities();

        Optional<TourActivity> pickup = tourActivities.stream().filter(this::isPickupActivityForTrackedJob).findFirst();
        Optional<TourActivity> delivery = tourActivities.stream().filter(this::isDeliveryActivityForTrackedJob).findFirst();

        // When a new pickupShipment activity will be inserted in a route, it can be accepted without checking
        // any further requirements.
        // Yet we need to store the pickupShipment activity and the next activity after the pickupShipment
        // in two temporary route states in order to either accept or reject the insertion
        // of deliverShipment as well since it requires the calculation of the travelTime.
        //
        // The shipment will only be finally set to be part of a route once the pickupShipment and
        // deliverShipment activities successfully pass this constraint.
        // Prior to this they are not part of `route.getActivities()` and are considered temporary.
        // This affects the insertion of deliverShipment as the calculation requires to compute the travel time
        // between 3 areas:
        // Area 1: pickupShipment and its adjacent activity (stored and retrieved via the stateManager)
        // Area 2: All already finalised tourActivities between the adjacent activity of the pickupShipment and
        //         the previous activity of the deliverShipment
        // Area 3: previous activity and the deliverShipment
        // Only if the criteria is met, the deliverShipment can be inserted to the route and both the pickup and
        // delivery will be part of `route.getActivities()` in the next iteration when other jobs get inserted.
        //
        // If the shipment is already part of the tour, we also need to take a closer look at the insert
        // position of newActivity but can rely this time solely on `route.getActivities` without considering the
        // previously set temporary states.
        if (isActivityForTrackedJob(newAct)) {
            if (isPickupActivityForTrackedJob(newAct)) {
                stateManager.putRouteState(route, temporaryPickupActivityStateId, newAct);
                stateManager.putRouteState(route, temporaryNextActivityStateId, nextAct);
            } else if (isDeliveryActivityForTrackedJob(newAct) && !isPickupActivityForTrackedJob(prevAct)) {
                // The deliverShipment activity (newAct) can be inserted without calculations if the previousActivity is either the
                // start of the route or the pickupShipment itself. This part here will only be executed otherwise.
                TourActivity temporaryPickupActivity = stateManager.getRouteState(route, temporaryPickupActivityStateId, TourActivity.class);
                TourActivity temporaryNextActivity = stateManager.getRouteState(route, temporaryNextActivityStateId, TourActivity.class);
                int temporaryNextActivityIndex = tourActivities.indexOf(temporaryNextActivity);
                int previousActivityIndex = tourActivities.indexOf(prevAct);
                List<TourActivity> adjacentToPreviousActivities = tourActivities.subList(temporaryNextActivityIndex, previousActivityIndex + 1);

                double totalTravelTime = calculateTravelTime(Arrays.asList(temporaryPickupActivity, temporaryNextActivity));
                totalTravelTime += calculateTravelTime(adjacentToPreviousActivities);
                totalTravelTime += calculateTravelTime(Arrays.asList(prevAct, newAct));
                if (totalTravelTime > maximumAllowedTravelTime) {
                    return ConstraintsStatus.NOT_FULFILLED;
                }
            }
        } else if (pickup.isPresent() && delivery.isPresent()) {
            int pickupIndex = tourActivities.indexOf(pickup.get());
            int dropoffIndex = tourActivities.indexOf(delivery.get());

            TourActivity plannedPreviousActivity = stateManager.getRouteState(route, plannedPreviousActivityStateId, TourActivity.class);
            TourActivity plannedNewActivity = stateManager.getRouteState(route, plannedNewActivityStateId, TourActivity.class);
            TourActivity plannedNextActivity = stateManager.getRouteState(route, plannedNextActivityStateId, TourActivity.class);
            List<TourActivity> searchSpace = tourActivities.subList(pickupIndex, dropoffIndex + 1);
            // We need to recalculate the maxTimeInVehicle when either both (prevAct and nextAct) are
            // within the search space (even if they are the only elements in there, thus being the shipment in question itself)
            // or if we successfully inserted any other pickupShipment activity, but did not evaluate the corresponding deliverShipment activity
            // yet. In that case the other pickupShipment activity will be stored as a temporary/planned Route State to have all the required
            // information to accept or reject its corresponding deliverShipment activity in the next iteration available.
            // If any of the mentioned conditions is not met, then the newAct (and its corresponding activities)
            // will be inserted either before or after the Job in question and we can happily accept that case.
            if (searchSpace.indexOf(prevAct) >= 0 && searchSpace.indexOf(nextAct) >= 0) {
                // In order to calculate the new maxInVehicle time we divide the search space in 3 different areas:
                // Area A: everything between the pickupShipment of the Job in question and the previousActivity (including it)
                // Area B: everything between the nextActivity and the deliverShipment of the Job in question (including it)
                // Area C: previousActivity, newActivity and nextActivity
                // With those 3 areas, we now can recompute the inVehicleTime and check if it exceeds the
                // maxInVehicleTime (maximumAllowedTravelTime).
                int previousActivityIndex = searchSpace.indexOf(prevAct);
                int nextActivityIndex = searchSpace.indexOf(nextAct);
                List<TourActivity> pickupToPreviousActivity = searchSpace.subList(0, previousActivityIndex + 1);
                List<TourActivity> nextActivityToDropoff = searchSpace.subList(nextActivityIndex, searchSpace.size());

                double totalTravelTime = calculateTravelTime(pickupToPreviousActivity);
                totalTravelTime += calculateTravelTime(nextActivityToDropoff);
                totalTravelTime += calculateTravelTime(Arrays.asList(prevAct, newAct, nextAct));

                if (totalTravelTime > maximumAllowedTravelTime) {
                    return ConstraintsStatus.NOT_FULFILLED;
                } else {
                    if (isPickup(newAct)) {
                        // A pickupShipment activity of any other job will be accepted, but is not yet part of `route.getActivities()`.
                        // We need to store all the involved activities as temporary/planned route states so we can evaluate
                        // its deliverShipment activity in the next iteration.
                        // We don't need to store the deliverShipment in a route state, as it is the final part
                        // of a booking and once accepted the whole booking is part of `route.getActivities()`.
                        stateManager.putRouteState(route, plannedPreviousActivityStateId, prevAct);
                        stateManager.putRouteState(route, plannedNewActivityStateId, newAct);
                        stateManager.putRouteState(route, plannedNextActivityStateId, nextAct);
                    }
                }
            } else if (prevAct.equals(plannedNewActivity) && nextAct.equals(plannedNextActivity)) {
                int previousActivityIndex = searchSpace.indexOf(plannedPreviousActivity);
                int nextActivityIndex = searchSpace.indexOf(plannedNextActivity);
                List<TourActivity> pickupToPrevAct = searchSpace.subList(0, previousActivityIndex + 1);
                List<TourActivity> nextActToDropoff = searchSpace.subList(nextActivityIndex, searchSpace.size());

                double totalTravelTime = calculateTravelTime(pickupToPrevAct);
                totalTravelTime += calculateTravelTime(Arrays.asList(plannedPreviousActivity, prevAct, newAct, nextAct));
                totalTravelTime += calculateTravelTime(nextActToDropoff);

                if (totalTravelTime > maximumAllowedTravelTime) {
                    return ConstraintsStatus.NOT_FULFILLED;
                }
            }
        }
        // in any other case
        return ConstraintsStatus.FULFILLED;
    }

    private boolean isActivityForTrackedJob(TourActivity activity){
        return ((TourActivity.JobActivity) activity).getJob().getId().equals(jobId);
    }

    private boolean isPickup(TourActivity activity){
        return activity.getName().equals("pickupShipment");
    }

    private boolean isDelivery(TourActivity activity){
        return activity.getName().equals("deliverShipment");
    }

    private boolean isPickupActivityForTrackedJob(TourActivity activity) {
        return isActivityForTrackedJob(activity) && isPickup(activity);
    }

    private boolean isDeliveryActivityForTrackedJob(TourActivity activity) {
        return isActivityForTrackedJob(activity) && isDelivery(activity);
    }

    private double calculateTravelTime(List<TourActivity> tourActivities) {
        final TourActivity[] from = {tourActivities.get(0)};
        return tourActivities.stream()
            .map(tourActivity -> {
                double travelTime = transportCostsMatrix.getTransportTime(from[0].getLocation(), tourActivity.getLocation(), 0., null, null);
                from[0] = tourActivity;
                return travelTime;
            })
            .reduce((totalDistance, legDistance) -> totalDistance + legDistance)
            .orElse(null);
    }
}
