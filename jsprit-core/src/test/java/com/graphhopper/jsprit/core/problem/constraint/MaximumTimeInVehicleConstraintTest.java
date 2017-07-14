package com.graphhopper.jsprit.core.problem.constraint;

import com.graphhopper.jsprit.core.algorithm.state.StateManager;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.job.Job;
import com.graphhopper.jsprit.core.problem.misc.JobInsertionContext;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.solution.route.activity.End;
import com.graphhopper.jsprit.core.problem.solution.route.activity.Start;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;
import com.graphhopper.jsprit.core.util.VehicleRoutingTransportCostsMatrix;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class MaximumTimeInVehicleConstraintTest {

    @Mock
    private VehicleRoutingProblem vehicleRoutingProblem;
    @Mock
    private JobInsertionContext context;
    @Mock
    private VehicleRoute route;
    @Mock
    private TourActivity.JobActivity myJobPickup;
    @Mock
    private TourActivity.JobActivity myJobDelivery;
    @Mock
    private TourActivity.JobActivity previousActivity;
    @Mock
    private TourActivity.JobActivity newActivity;
    @Mock
    private TourActivity.JobActivity nextActivity;
    @Mock
    private TourActivity.JobActivity anyActivity;
    private Start vehicleStart;
    private End vehicleEnd;
    @Mock
    private Job myJob;
    @Mock
    private Job newActivityJob;
    @Mock
    private VehicleRoutingTransportCostsMatrix transportCostsMatrix;

    private StateManager stateManager;
    private List<TourActivity> tourActivities;

    @Before
    public void doBefore() throws Exception {
        stateManager = new StateManager(vehicleRoutingProblem);
        tourActivities = new ArrayList<>();
        vehicleEnd = End.newInstance("vehicle:end", 0., 0.);
        given(context.getRoute()).willReturn(route);
        given(route.getActivities()).willReturn(tourActivities);
        given(newActivity.getJob()).willReturn(newActivityJob);
    }

    @Test
    public void shouldFulfillConstraint_MyJobIsNotPresent() throws Exception {
        // given
        given(newActivityJob.getId()).willReturn("foo");
        MaximumTimeInVehicleConstraint constraint = new MaximumTimeInVehicleConstraint(200., transportCostsMatrix, stateManager, "myJob");

        // when
        HardActivityConstraint.ConstraintsStatus constraintStatus = constraint.fulfilled(context, previousActivity, newActivity, nextActivity, 0.);

        // assert
        Assert.assertEquals(HardActivityConstraint.ConstraintsStatus.FULFILLED, constraintStatus);
    }

    @Test
    public void shouldFulfillConstraint_MyJobPickupGetsInsertedAnywhere() throws Exception {
        // given
        given(newActivity.getName()).willReturn("pickupShipment");
        given(newActivityJob.getId()).willReturn("myJob");
        MaximumTimeInVehicleConstraint constraint = new MaximumTimeInVehicleConstraint(200., transportCostsMatrix, stateManager, "myJob");

        // when
        HardActivityConstraint.ConstraintsStatus constraintStatus = constraint.fulfilled(context, previousActivity, newActivity, nextActivity, 0.);

        // assert
        Assert.assertEquals(HardActivityConstraint.ConstraintsStatus.FULFILLED, constraintStatus);
    }


    @Test
    public void shouldFulfillConstraint_MyJobPickupGetsInsertedInEmptyRoute() throws Exception {
        // given
        given(newActivity.getName()).willReturn("pickupShipment");
        given(newActivityJob.getId()).willReturn("myJob");

        MaximumTimeInVehicleConstraint constraint = new MaximumTimeInVehicleConstraint(200., transportCostsMatrix, stateManager, "myJob");

        // when
        HardActivityConstraint.ConstraintsStatus constraintStatus = constraint.fulfilled(context, vehicleStart, newActivity, vehicleEnd, 0.);

        // assert
        Assert.assertEquals(HardActivityConstraint.ConstraintsStatus.FULFILLED, constraintStatus);
    }

    @Test
    public void shouldFulfillConstraint_MyJobInsertionWillNotExceedTravelTimeAndWrapsExistingJob() throws Exception {
        // given
        TourActivity.JobActivity activityOne = mock(TourActivity.JobActivity.class);
        Job activityOneJob = mock(Job.class);
        given(myJobPickup.getName()).willReturn("pickupShipment");
        given(myJobDelivery.getName()).willReturn("deliverShipment");
        given(myJobPickup.getJob()).willReturn(myJob);
        given(myJobDelivery.getJob()).willReturn(myJob);
        given(myJob.getId()).willReturn("myJob");
        given(activityOne.getJob()).willReturn(activityOneJob);
        given(activityOneJob.getId()).willReturn("otherJob");
        tourActivities.add(activityOne);

        Location myJobPickupLocation = Location.newInstance("myJob:pickup");
        Location myJobDeliveryLocation = Location.newInstance("myJob:delivery");
        Location activityOneLocation = Location.newInstance("otherJob:pickup");
        given(myJobPickup.getLocation()).willReturn(myJobPickupLocation);
        given(myJobDelivery.getLocation()).willReturn(myJobDeliveryLocation);
        given(activityOne.getLocation()).willReturn(activityOneLocation);

        given(transportCostsMatrix.getTransportTime(eq(myJobPickupLocation), eq(myJobPickupLocation), eq(0), eq(null), eq(null))).willReturn(0.);
        given(transportCostsMatrix.getTransportTime(eq(myJobPickupLocation), eq(activityOneLocation), eq(0), eq(null), eq(null))).willReturn(50.);
        given(transportCostsMatrix.getTransportTime(eq(activityOneLocation), eq(activityOneLocation), eq(0), eq(null), eq(null))).willReturn(0.);
        given(transportCostsMatrix.getTransportTime(eq(activityOneLocation), eq(myJobDeliveryLocation), eq(0), eq(null), eq(null))).willReturn(100.);

        MaximumTimeInVehicleConstraint constraint = new MaximumTimeInVehicleConstraint(200., transportCostsMatrix, stateManager, "myJob");
        constraint.fulfilled(context, vehicleStart, myJobPickup, activityOne, 0.);

        // when
        HardActivityConstraint.ConstraintsStatus constraintStatus = constraint.fulfilled(context, activityOne, myJobDelivery, vehicleEnd, 0.);

        // assert
        Assert.assertEquals(HardActivityConstraint.ConstraintsStatus.FULFILLED, constraintStatus);
    }

    @Test
    public void shouldRejectConstraint_MyJobInsertionWillExceedTravelTimeAndAndWrapsMultipleExistingJobs() throws Exception {
        // given
        TourActivity.JobActivity activityOne = mock(TourActivity.JobActivity.class);
        TourActivity.JobActivity activityTwo = mock(TourActivity.JobActivity.class);
        TourActivity.JobActivity activityThree = mock(TourActivity.JobActivity.class);
        Job activityOneJob = mock(Job.class);
        Job activityTwoJob = mock(Job.class);
        Job activityThreeJob = mock(Job.class);
        given(myJobPickup.getName()).willReturn("pickupShipment");
        given(myJobDelivery.getName()).willReturn("deliverShipment");
        given(myJobPickup.getJob()).willReturn(myJob);
        given(myJobDelivery.getJob()).willReturn(myJob);
        given(myJob.getId()).willReturn("myJob");
        given(activityOne.getJob()).willReturn(activityOneJob);
        given(activityOneJob.getId()).willReturn("foobar");
        given(activityTwo.getJob()).willReturn(activityTwoJob);
        given(activityTwoJob.getId()).willReturn("foobar");
        given(activityThree.getJob()).willReturn(activityThreeJob);
        given(activityThreeJob.getId()).willReturn("eggbaz");
        tourActivities.add(activityOne);
        tourActivities.add(activityTwo);
        tourActivities.add(activityThree);

        Location myJobPickupLocation = Location.newInstance("myJob:pickup");
        Location myJobDeliveryLocation = Location.newInstance("myJob:delivery");
        Location activityTwoLocation = Location.newInstance("foobar:delivery");
        Location activityThreeLocation = Location.newInstance("spam:pickup");
        given(myJobPickup.getLocation()).willReturn(myJobPickupLocation);
        given(myJobDelivery.getLocation()).willReturn(myJobDeliveryLocation);
        given(activityTwo.getLocation()).willReturn(activityTwoLocation);
        given(activityThree.getLocation()).willReturn(activityThreeLocation);

        given(transportCostsMatrix.getTransportTime(eq(myJobPickupLocation), eq(myJobPickupLocation), eq(0.), eq(null), eq(null))).willReturn(0.);
        given(transportCostsMatrix.getTransportTime(eq(activityTwoLocation), eq(activityTwoLocation), eq(0.), eq(null), eq(null))).willReturn(0.);
        given(transportCostsMatrix.getTransportTime(eq(activityThreeLocation), eq(activityThreeLocation), eq(0.), eq(null), eq(null))).willReturn(0.);

        given(transportCostsMatrix.getTransportTime(eq(myJobPickupLocation), eq(activityTwoLocation), eq(0.), eq(null), eq(null))).willReturn(100.);
        given(transportCostsMatrix.getTransportTime(eq(activityTwoLocation), eq(activityThreeLocation), eq(0.), eq(null), eq(null))).willReturn(100.);
        given(transportCostsMatrix.getTransportTime(eq(activityThreeLocation), eq(myJobDeliveryLocation), eq(0.), eq(null), eq(null))).willReturn(100.);

        MaximumTimeInVehicleConstraint constraint = new MaximumTimeInVehicleConstraint(200., transportCostsMatrix, stateManager, "myJob");
        constraint.fulfilled(context, activityOne, myJobPickup, activityTwo, 0.);

        // when
        HardActivityConstraint.ConstraintsStatus constraintStatus = constraint.fulfilled(context, activityThree, myJobDelivery, vehicleEnd, 0.);

        // assert
        Assert.assertEquals(HardActivityConstraint.ConstraintsStatus.NOT_FULFILLED, constraintStatus);
    }

    @Test
    public void shouldFulfillConstraint_MyJobInsertionWillNotExceedTravelTimeAndWrapsMultipleExistingBookings() throws Exception {
        // given
        TourActivity.JobActivity activityOne = mock(TourActivity.JobActivity.class);
        TourActivity.JobActivity activityTwo = mock(TourActivity.JobActivity.class);
        TourActivity.JobActivity activityThree = mock(TourActivity.JobActivity.class);
        Job activityOneJob = mock(Job.class);
        Job activityTwoJob = mock(Job.class);
        Job activityThreeJob = mock(Job.class);
        given(myJobPickup.getName()).willReturn("pickupShipment");
        given(myJobDelivery.getName()).willReturn("deliverShipment");
        given(myJobPickup.getJob()).willReturn(myJob);
        given(myJobDelivery.getJob()).willReturn(myJob);
        given(myJob.getId()).willReturn("myJob");
        given(activityOne.getJob()).willReturn(activityOneJob);
        given(activityOneJob.getId()).willReturn("foobar");
        given(activityTwo.getJob()).willReturn(activityTwoJob);
        given(activityTwoJob.getId()).willReturn("foobar");
        given(activityThree.getJob()).willReturn(activityThreeJob);
        given(activityThreeJob.getId()).willReturn("eggbaz");
        tourActivities.add(activityOne);
        tourActivities.add(activityTwo);
        tourActivities.add(activityThree);

        Location myJobPickupLocation = Location.newInstance("myJob:pickup");
        Location myJobDeliveryLocation = Location.newInstance("myJob:delivery");
        Location activityTwoLocation = Location.newInstance("foobar:delivery");
        Location activityThreeLocation = Location.newInstance("spam:pickup");
        given(myJobPickup.getLocation()).willReturn(myJobPickupLocation);
        given(myJobDelivery.getLocation()).willReturn(myJobDeliveryLocation);
        given(activityTwo.getLocation()).willReturn(activityTwoLocation);
        given(activityThree.getLocation()).willReturn(activityThreeLocation);

        given(transportCostsMatrix.getTransportTime(eq(myJobPickupLocation), eq(myJobPickupLocation), eq(0.), eq(null), eq(null))).willReturn(0.);
        given(transportCostsMatrix.getTransportTime(eq(activityTwoLocation), eq(activityTwoLocation), eq(0.), eq(null), eq(null))).willReturn(0.);
        given(transportCostsMatrix.getTransportTime(eq(activityThreeLocation), eq(activityThreeLocation), eq(0.), eq(null), eq(null))).willReturn(0.);

        given(transportCostsMatrix.getTransportTime(eq(myJobPickupLocation), eq(activityTwoLocation), eq(0.), eq(null), eq(null))).willReturn(100.);
        given(transportCostsMatrix.getTransportTime(eq(activityTwoLocation), eq(activityThreeLocation), eq(0.), eq(null), eq(null))).willReturn(100.);
        given(transportCostsMatrix.getTransportTime(eq(activityThreeLocation), eq(myJobDeliveryLocation), eq(0.), eq(null), eq(null))).willReturn(100.);

        MaximumTimeInVehicleConstraint constraint = new MaximumTimeInVehicleConstraint(400., transportCostsMatrix, stateManager, "myJob");
        constraint.fulfilled(context, activityOne, myJobPickup, activityTwo, 0.);

        // when
        HardActivityConstraint.ConstraintsStatus constraintStatus = constraint.fulfilled(context, activityThree, myJobDelivery, vehicleEnd, 0.);

        // assert
        Assert.assertEquals(HardActivityConstraint.ConstraintsStatus.FULFILLED, constraintStatus);
    }

    @Test
    public void shouldFulfillConstraint_MyJobDeliveryGetsInsertedAfterMyJobPickupAndWillNotExceedTravelTime() throws Exception {
        // given
        given(newActivity.getName()).willReturn("deliverShipment");
        given(newActivityJob.getId()).willReturn("myJob");
        given(myJobPickup.getName()).willReturn("pickupShipment");
        given(myJobDelivery.getName()).willReturn("deliverShipment");
        given(myJobPickup.getJob()).willReturn(myJob);
        given(myJob.getId()).willReturn("myJob");
        MaximumTimeInVehicleConstraint constraint = new MaximumTimeInVehicleConstraint(200., transportCostsMatrix, stateManager, "myJob");
        constraint.fulfilled(context, vehicleStart, myJobPickup, vehicleEnd, 0.);

        // when
        HardActivityConstraint.ConstraintsStatus constraintStatus = constraint.fulfilled(context, myJobPickup, newActivity, vehicleEnd, 0.);

        // assert
        Assert.assertEquals(HardActivityConstraint.ConstraintsStatus.FULFILLED, constraintStatus);
    }

    @Test
    public void shouldFulfillConstraint_NewActivityGetsInsertedBeforeMyJobAndWillNotExceedTravelTime() throws Exception {
        // given
        given(myJobPickup.getName()).willReturn("pickupShipment");
        given(myJobDelivery.getName()).willReturn("deliverShipment");
        given(myJobPickup.getJob()).willReturn(myJob);
        given(myJobDelivery.getJob()).willReturn(myJob);
        given(myJob.getId()).willReturn("myJob");
        given(newActivity.getJob()).willReturn(newActivityJob);
        given(newActivityJob.getId()).willReturn("foobar");
        tourActivities.add(myJobPickup);
        tourActivities.add(myJobDelivery);
        previousActivity = anyActivity;
        nextActivity = myJobPickup;
        MaximumTimeInVehicleConstraint constraint = new MaximumTimeInVehicleConstraint(200., transportCostsMatrix, stateManager, "myJob");

        // when
        HardActivityConstraint.ConstraintsStatus constraintStatus = constraint.fulfilled(context, previousActivity, newActivity, nextActivity, 0.);

        // assert
        Assert.assertEquals(HardActivityConstraint.ConstraintsStatus.FULFILLED, constraintStatus);
    }

    @Test
    public void shouldFulfillConstraint_NewActivityGetsInsertedAfterMyJobAndWillNotExceedTravelTime() throws Exception {
        // given
        given(myJobPickup.getName()).willReturn("pickupShipment");
        given(myJobDelivery.getName()).willReturn("deliverShipment");
        given(myJobPickup.getJob()).willReturn(myJob);
        given(myJobDelivery.getJob()).willReturn(myJob);
        given(myJob.getId()).willReturn("myJob");
        given(newActivity.getJob()).willReturn(newActivityJob);
        given(newActivityJob.getId()).willReturn("foobar");
        tourActivities.add(myJobPickup);
        tourActivities.add(myJobDelivery);
        previousActivity = myJobDelivery;
        nextActivity = anyActivity;
        MaximumTimeInVehicleConstraint constraint = new MaximumTimeInVehicleConstraint(200., transportCostsMatrix, stateManager, "myJob");

        // when
        HardActivityConstraint.ConstraintsStatus constraintStatus = constraint.fulfilled(context, previousActivity, newActivity, nextActivity, 0.);

        // assert
        Assert.assertEquals(HardActivityConstraint.ConstraintsStatus.FULFILLED, constraintStatus);
    }


    @Test
    public void shouldFulfillConstraint_NewActivityGetsInsertedDirectlyBetweenPickupAndDeliveryAndWillNotExceedTravelTime() throws Exception {
        // given
        given(myJobPickup.getName()).willReturn("pickupShipment");
        given(myJobDelivery.getName()).willReturn("deliverShipment");
        given(myJobPickup.getJob()).willReturn(myJob);
        given(myJobDelivery.getJob()).willReturn(myJob);
        given(myJob.getId()).willReturn("myJob");
        given(newActivity.getName()).willReturn("deliverShipment");
        given(newActivity.getJob()).willReturn(newActivityJob);
        given(newActivityJob.getId()).willReturn("foobar");
        tourActivities.add(myJobPickup);
        tourActivities.add(myJobDelivery);
        previousActivity = myJobPickup;
        nextActivity = myJobDelivery;

        Location myJobPickupLocation = Location.newInstance("myJob:pickup");
        Location myJobDeliveryLocation = Location.newInstance("myJob:delivery");
        Location newActivityLocation = Location.newInstance("foobar:delivery");

        given(myJobPickup.getLocation()).willReturn(myJobPickupLocation);
        given(myJobDelivery.getLocation()).willReturn(myJobDeliveryLocation);
        given(previousActivity.getLocation()).willReturn(myJobPickupLocation);
        given(newActivity.getLocation()).willReturn(newActivityLocation);
        given(nextActivity.getLocation()).willReturn(myJobDeliveryLocation);

        given(transportCostsMatrix.getTransportTime(eq(myJobPickupLocation), eq(myJobPickupLocation), eq(0.), eq(null), eq(null))).willReturn(0.);
        given(transportCostsMatrix.getTransportTime(eq(myJobPickupLocation), eq(newActivityLocation), eq(0.), eq(null), eq(null))).willReturn(50.);
        given(transportCostsMatrix.getTransportTime(eq(newActivityLocation), eq(myJobDeliveryLocation), eq(0.), eq(null), eq(null))).willReturn(100.);
        MaximumTimeInVehicleConstraint constraint = new MaximumTimeInVehicleConstraint(150., transportCostsMatrix, stateManager, "myJob");

        // when
        HardActivityConstraint.ConstraintsStatus constraintStatus = constraint.fulfilled(context, previousActivity, newActivity, nextActivity, 0.);

        // assert
        Assert.assertEquals(HardActivityConstraint.ConstraintsStatus.FULFILLED, constraintStatus);
    }

    @Test
    public void shouldRejectConstraint_NewActivityGetsInsertedDirectlyBetweenPickupAndDeliveryAndWillExceedTravelTime() throws Exception {
        // given
        given(myJobPickup.getName()).willReturn("pickupShipment");
        given(myJobDelivery.getName()).willReturn("deliverShipment");
        given(myJobPickup.getJob()).willReturn(myJob);
        given(myJobDelivery.getJob()).willReturn(myJob);
        given(myJob.getId()).willReturn("myJob");
        given(newActivity.getName()).willReturn("deliverShipment");
        given(newActivity.getJob()).willReturn(newActivityJob);
        given(newActivityJob.getId()).willReturn("foobar");
        tourActivities.add(myJobPickup);
        tourActivities.add(myJobDelivery);
        previousActivity = myJobPickup;
        nextActivity = myJobDelivery;

        Location myJobPickupLocation = Location.newInstance("myJob:pickup");
        Location myJobDeliveryLocation = Location.newInstance("myJob:delivery");
        Location newActivityLocation = Location.newInstance("foobar:delivery");

        given(myJobPickup.getLocation()).willReturn(myJobPickupLocation);
        given(myJobDelivery.getLocation()).willReturn(myJobDeliveryLocation);
        given(previousActivity.getLocation()).willReturn(myJobPickupLocation);
        given(newActivity.getLocation()).willReturn(newActivityLocation);
        given(nextActivity.getLocation()).willReturn(myJobDeliveryLocation);

        given(transportCostsMatrix.getTransportTime(eq(myJobPickupLocation), eq(myJobPickupLocation), eq(0.), eq(null), eq(null))).willReturn(0.);
        given(transportCostsMatrix.getTransportTime(eq(myJobPickupLocation), eq(newActivityLocation), eq(0.), eq(null), eq(null))).willReturn(200.);
        given(transportCostsMatrix.getTransportTime(eq(newActivityLocation), eq(myJobDeliveryLocation), eq(0.), eq(null), eq(null))).willReturn(100.);
        MaximumTimeInVehicleConstraint constraint = new MaximumTimeInVehicleConstraint(150., transportCostsMatrix, stateManager, "myJob");

        // when
        HardActivityConstraint.ConstraintsStatus constraintStatus = constraint.fulfilled(context, previousActivity, newActivity, nextActivity, 0.);

        // assert
        Assert.assertEquals(HardActivityConstraint.ConstraintsStatus.NOT_FULFILLED, constraintStatus);
    }

    @Test
    public void shouldFulfillConstraint_NewActivityGetsInsertedSomewhereBetweenPickupAndDeliveryAndWillNotExceedTravelTime() throws Exception {
        // given
        TourActivity.JobActivity activityOne = mock(TourActivity.JobActivity.class);
        TourActivity.JobActivity activityTwo = mock(TourActivity.JobActivity.class);
        TourActivity.JobActivity activityThree = mock(TourActivity.JobActivity.class);
        Job activityOneJob = mock(Job.class);
        Job activityTwoJob = mock(Job.class);
        Job activityThreeJob = mock(Job.class);
        given(myJobPickup.getName()).willReturn("pickupShipment");
        given(myJobDelivery.getName()).willReturn("deliverShipment");
        given(myJobPickup.getJob()).willReturn(myJob);
        given(myJobDelivery.getJob()).willReturn(myJob);
        given(myJob.getId()).willReturn("myJob");
        given(activityOne.getJob()).willReturn(activityOneJob);
        given(activityOneJob.getId()).willReturn("foobar");
        given(activityTwo.getJob()).willReturn(activityTwoJob);
        given(activityTwoJob.getId()).willReturn("foobar");
        given(activityThree.getJob()).willReturn(activityThreeJob);
        given(activityThreeJob.getId()).willReturn("spamegg");
        given(newActivity.getName()).willReturn("deliverShipment");
        given(newActivity.getJob()).willReturn(newActivityJob);
        given(newActivityJob.getId()).willReturn("baz");
        tourActivities.add(myJobPickup);
        tourActivities.add(activityOne);
        tourActivities.add(activityTwo);
        tourActivities.add(activityThree);
        tourActivities.add(myJobDelivery);
        previousActivity = activityOne;
        nextActivity = activityTwo;

        Location myJobPickupLocation = Location.newInstance("myJob:pickup");
        Location myJobDeliveryLocation = Location.newInstance("myJob:delivery");
        Location activityOneLocation = Location.newInstance("foobar:pickup");
        Location activityTwoLocation = Location.newInstance("foobar:delivery");
        Location activityThreeLocation = Location.newInstance("spamegg:pickup");
        Location newActivityLocation = Location.newInstance("baz:delivery");
        given(myJobPickup.getLocation()).willReturn(myJobPickupLocation);
        given(myJobDelivery.getLocation()).willReturn(myJobPickupLocation);
        given(activityOne.getLocation()).willReturn(activityOneLocation);
        given(activityTwo.getLocation()).willReturn(activityTwoLocation);
        given(activityThree.getLocation()).willReturn(activityThreeLocation);
        given(previousActivity.getLocation()).willReturn(activityOneLocation);
        given(newActivity.getLocation()).willReturn(newActivityLocation);
        given(nextActivity.getLocation()).willReturn(activityTwoLocation);

        given(transportCostsMatrix.getTransportTime(eq(myJobPickupLocation), eq(myJobPickupLocation), eq(0.), eq(null), eq(null))).willReturn(0.);
        given(transportCostsMatrix.getTransportTime(eq(myJobPickupLocation), eq(activityOneLocation), eq(0.), eq(null), eq(null))).willReturn(50.);
        given(transportCostsMatrix.getTransportTime(eq(activityOneLocation), eq(activityOneLocation), eq(0.), eq(null), eq(null))).willReturn(0.);
        given(transportCostsMatrix.getTransportTime(eq(activityOneLocation), eq(newActivityLocation), eq(0.), eq(null), eq(null))).willReturn(100.);
        given(transportCostsMatrix.getTransportTime(eq(newActivityLocation), eq(activityTwoLocation), eq(0.), eq(null), eq(null))).willReturn(100.);
        given(transportCostsMatrix.getTransportTime(eq(activityTwoLocation), eq(activityTwoLocation), eq(0.), eq(null), eq(null))).willReturn(0.);
        given(transportCostsMatrix.getTransportTime(eq(activityTwoLocation), eq(activityThreeLocation), eq(0.), eq(null), eq(null))).willReturn(50.);
        given(transportCostsMatrix.getTransportTime(eq(activityThreeLocation), eq(myJobDeliveryLocation), eq(0.), eq(null), eq(null))).willReturn(50.);

        MaximumTimeInVehicleConstraint constraint = new MaximumTimeInVehicleConstraint(350., transportCostsMatrix, stateManager, "myJob");

        // when
        HardActivityConstraint.ConstraintsStatus constraintStatus = constraint.fulfilled(context, previousActivity, newActivity, nextActivity, 0.);

        // assert
        Assert.assertEquals(HardActivityConstraint.ConstraintsStatus.FULFILLED, constraintStatus);
    }

    @Test
    public void shouldRejectConstraint_NewActivityGetsInsertedSomewhereBetweenPickupAndDeliveryAndWillExceedTravelTime() throws Exception {
        // given
        TourActivity.JobActivity activityOne = mock(TourActivity.JobActivity.class);
        TourActivity.JobActivity activityTwo = mock(TourActivity.JobActivity.class);
        TourActivity.JobActivity activityThree = mock(TourActivity.JobActivity.class);
        Job activityOneJob = mock(Job.class);
        Job activityTwoJob = mock(Job.class);
        Job activityThreeJob = mock(Job.class);
        given(myJobPickup.getName()).willReturn("pickupShipment");
        given(myJobDelivery.getName()).willReturn("deliverShipment");
        given(myJobPickup.getJob()).willReturn(myJob);
        given(myJobDelivery.getJob()).willReturn(myJob);
        given(myJob.getId()).willReturn("myJob");
        given(activityOne.getJob()).willReturn(activityOneJob);
        given(activityOneJob.getId()).willReturn("foobar");
        given(activityTwo.getJob()).willReturn(activityTwoJob);
        given(activityTwoJob.getId()).willReturn("foobar");
        given(activityThree.getJob()).willReturn(activityThreeJob);
        given(activityThreeJob.getId()).willReturn("spamegg");
        given(newActivity.getName()).willReturn("deliverShipment");
        given(newActivity.getJob()).willReturn(newActivityJob);
        given(newActivityJob.getId()).willReturn("baz");
        tourActivities.add(myJobPickup);
        tourActivities.add(activityOne);
        tourActivities.add(activityTwo);
        tourActivities.add(activityThree);
        tourActivities.add(myJobDelivery);
        previousActivity = activityOne;
        nextActivity = activityTwo;

        Location myJobPickupLocation = Location.newInstance("myJob:pickup");
        Location myJobDeliveryLocation = Location.newInstance("myJob:delivery");
        Location activityOneLocation = Location.newInstance("foobar:pickup");
        Location activityTwoLocation = Location.newInstance("foobar:delivery");
        Location activityThreeLocation = Location.newInstance("spamegg:pickup");
        Location newActivityLocation = Location.newInstance("baz:delivery");
        given(myJobPickup.getLocation()).willReturn(myJobPickupLocation);
        given(myJobDelivery.getLocation()).willReturn(myJobPickupLocation);
        given(activityOne.getLocation()).willReturn(activityOneLocation);
        given(activityTwo.getLocation()).willReturn(activityTwoLocation);
        given(activityThree.getLocation()).willReturn(activityThreeLocation);
        given(previousActivity.getLocation()).willReturn(activityOneLocation);
        given(newActivity.getLocation()).willReturn(newActivityLocation);
        given(nextActivity.getLocation()).willReturn(activityTwoLocation);

        given(transportCostsMatrix.getTransportTime(eq(myJobPickupLocation), eq(myJobPickupLocation), eq(0.), eq(null), eq(null))).willReturn(0.);
        given(transportCostsMatrix.getTransportTime(eq(myJobPickupLocation), eq(activityOneLocation), eq(0.), eq(null), eq(null))).willReturn(50.);
        given(transportCostsMatrix.getTransportTime(eq(activityOneLocation), eq(activityOneLocation), eq(0.), eq(null), eq(null))).willReturn(0.);
        given(transportCostsMatrix.getTransportTime(eq(activityOneLocation), eq(newActivityLocation), eq(0.), eq(null), eq(null))).willReturn(500.);
        given(transportCostsMatrix.getTransportTime(eq(newActivityLocation), eq(activityTwoLocation), eq(0.), eq(null), eq(null))).willReturn(100.);
        given(transportCostsMatrix.getTransportTime(eq(activityTwoLocation), eq(activityTwoLocation), eq(0.), eq(null), eq(null))).willReturn(0.);
        given(transportCostsMatrix.getTransportTime(eq(activityTwoLocation), eq(activityThreeLocation), eq(0.), eq(null), eq(null))).willReturn(50.);
        given(transportCostsMatrix.getTransportTime(eq(activityThreeLocation), eq(myJobDeliveryLocation), eq(0.), eq(null), eq(null))).willReturn(50.);

        MaximumTimeInVehicleConstraint constraint = new MaximumTimeInVehicleConstraint(350., transportCostsMatrix, stateManager, "myJob");

        // when
        HardActivityConstraint.ConstraintsStatus constraintStatus = constraint.fulfilled(context, previousActivity, newActivity, nextActivity, 0.);

        // assert
        Assert.assertEquals(HardActivityConstraint.ConstraintsStatus.NOT_FULFILLED, constraintStatus);
    }

    @Test
    public void shouldFulfillConstraint_ShipmentGetsInsertedBetweenMyJobAndWillNotExceedTravelTime() throws Exception {
        // given
        TourActivity.JobActivity shipmentPickup = mock(TourActivity.JobActivity.class);
        TourActivity.JobActivity shipmentDelivery = mock(TourActivity.JobActivity.class);
        Job shipment = mock(Job.class);
        given(myJobPickup.getName()).willReturn("pickupShipment");
        given(myJobDelivery.getName()).willReturn("deliverShipment");
        given(myJobPickup.getJob()).willReturn(myJob);
        given(myJobDelivery.getJob()).willReturn(myJob);
        given(myJob.getId()).willReturn("myJob");
        given(shipmentPickup.getName()).willReturn("pickupShipment");
        given(shipmentPickup.getJob()).willReturn(shipment);
        given(shipmentDelivery.getJob()).willReturn(shipment);
        given(shipment.getId()).willReturn("anotherShipment");
        tourActivities.add(myJobPickup);
        tourActivities.add(myJobDelivery);

        Location myJobPickupLocation = Location.newInstance("myJob:pickup");
        Location myJobDeliveryLocation = Location.newInstance("myJob:delivery");
        Location shipmentPickupLocation = Location.newInstance("anotherShipment:pickup");
        Location shipmentDeliveryLocation = Location.newInstance("anotherShipment:delivery");
        given(myJobPickup.getLocation()).willReturn(myJobPickupLocation);
        given(myJobDelivery.getLocation()).willReturn(myJobDeliveryLocation);
        given(shipmentPickup.getLocation()).willReturn(shipmentPickupLocation);
        given(shipmentDelivery.getLocation()).willReturn(shipmentDeliveryLocation);

        given(transportCostsMatrix.getTransportTime(eq(myJobPickupLocation), eq(myJobPickupLocation), eq(0.), eq(null), eq(null))).willReturn(0.);
        given(transportCostsMatrix.getTransportTime(eq(myJobPickupLocation), eq(shipmentPickupLocation), eq(0.), eq(null), eq(null))).willReturn(50.);
        given(transportCostsMatrix.getTransportTime(eq(shipmentPickupLocation), eq(shipmentDeliveryLocation), eq(0.), eq(null), eq(null))).willReturn(75.);
        given(transportCostsMatrix.getTransportTime(eq(shipmentPickupLocation), eq(myJobDeliveryLocation), eq(0.), eq(null), eq(null))).willReturn(75.);
        given(transportCostsMatrix.getTransportTime(eq(shipmentDeliveryLocation), eq(myJobDeliveryLocation), eq(0.), eq(null), eq(null))).willReturn(150.);
        given(transportCostsMatrix.getTransportTime(eq(myJobDeliveryLocation), eq(myJobDeliveryLocation), eq(0.), eq(null), eq(null))).willReturn(0.);

        MaximumTimeInVehicleConstraint constraint = new MaximumTimeInVehicleConstraint(350., transportCostsMatrix, stateManager, "myJob");

        // when
        HardActivityConstraint.ConstraintsStatus constraintStatusPickup = constraint.fulfilled(context, myJobPickup, shipmentPickup, myJobDelivery, 0.);

        // assert
        Assert.assertEquals(HardActivityConstraint.ConstraintsStatus.FULFILLED, constraintStatusPickup);

        // when
        HardActivityConstraint.ConstraintsStatus constraintStatusDelivery = constraint.fulfilled(context, shipmentPickup, shipmentDelivery, myJobDelivery, 0.);

        // assert
        Assert.assertEquals(HardActivityConstraint.ConstraintsStatus.FULFILLED, constraintStatusDelivery);
    }

    @Test
    public void shouldRejectConstraint_NewShipmentGetsInsertedBetweenMyJobAndWillNotExceedTravelTime() throws Exception {
        // given
        TourActivity.JobActivity shipmentPickup = mock(TourActivity.JobActivity.class);
        TourActivity.JobActivity shipmentDelivery = mock(TourActivity.JobActivity.class);
        Job shipment = mock(Job.class);
        given(myJobPickup.getName()).willReturn("pickupShipment");
        given(myJobDelivery.getName()).willReturn("deliverShipment");
        given(myJobPickup.getJob()).willReturn(myJob);
        given(myJobDelivery.getJob()).willReturn(myJob);
        given(myJob.getId()).willReturn("myJob");
        given(shipmentPickup.getName()).willReturn("pickupShipment");
        given(shipmentPickup.getJob()).willReturn(shipment);
        given(shipmentDelivery.getJob()).willReturn(shipment);
        given(shipment.getId()).willReturn("anotherShipment");
        tourActivities.add(myJobPickup);
        tourActivities.add(myJobDelivery);

        Location myJobPickupLocation = Location.newInstance("myJob:pickup");
        Location myJobDeliveryLocation = Location.newInstance("myJob:delivery");
        Location shipmentPickupLocation = Location.newInstance("anotherShipment:pickup");
        Location shipmentDeliveryLocation = Location.newInstance("anotherShipment:delivery");
        given(myJobPickup.getLocation()).willReturn(myJobPickupLocation);
        given(myJobDelivery.getLocation()).willReturn(myJobDeliveryLocation);
        given(shipmentPickup.getLocation()).willReturn(shipmentPickupLocation);
        given(shipmentDelivery.getLocation()).willReturn(shipmentDeliveryLocation);

        given(transportCostsMatrix.getTransportTime(eq(myJobPickupLocation), eq(myJobPickupLocation), eq(0.), eq(null), eq(null))).willReturn(0.);
        given(transportCostsMatrix.getTransportTime(eq(myJobPickupLocation), eq(shipmentPickupLocation), eq(0.), eq(null), eq(null))).willReturn(50.);
        given(transportCostsMatrix.getTransportTime(eq(shipmentPickupLocation), eq(shipmentDeliveryLocation), eq(0.), eq(null), eq(null))).willReturn(500.);
        given(transportCostsMatrix.getTransportTime(eq(shipmentPickupLocation), eq(myJobDeliveryLocation), eq(0.), eq(null), eq(null))).willReturn(75.);
        given(transportCostsMatrix.getTransportTime(eq(shipmentDeliveryLocation), eq(myJobDeliveryLocation), eq(0.), eq(null), eq(null))).willReturn(150.);
        given(transportCostsMatrix.getTransportTime(eq(myJobDeliveryLocation), eq(myJobDeliveryLocation), eq(0.), eq(null), eq(null))).willReturn(0.);

        MaximumTimeInVehicleConstraint constraint = new MaximumTimeInVehicleConstraint(350., transportCostsMatrix, stateManager, "myJob");

        // when
        HardActivityConstraint.ConstraintsStatus constraintStatusPickup = constraint.fulfilled(context, myJobPickup, shipmentPickup, myJobDelivery, 0.);

        // assert
        Assert.assertEquals(HardActivityConstraint.ConstraintsStatus.FULFILLED, constraintStatusPickup);

        // when
        HardActivityConstraint.ConstraintsStatus constraintStatusDelivery = constraint.fulfilled(context, shipmentPickup, shipmentDelivery, myJobDelivery, 0.);

        // assert
        Assert.assertEquals(HardActivityConstraint.ConstraintsStatus.NOT_FULFILLED, constraintStatusDelivery);
    }

    @Test
    public void shouldRejectConstraint_NewShipmentGetsInsertedBetweenMyJobAndAnotherShipmentAndWillExceedTravelTime() throws Exception {
        // given
        TourActivity.JobActivity existingShipmentPickup = mock(TourActivity.JobActivity.class);
        TourActivity.JobActivity existingShipmentDelivery = mock(TourActivity.JobActivity.class);
        Job existingShipment = mock(Job.class);
        TourActivity.JobActivity newShipmentPickup = mock(TourActivity.JobActivity.class);
        TourActivity.JobActivity newShipmentDelivery = mock(TourActivity.JobActivity.class);
        Job newShipment = mock(Job.class);
        given(myJobPickup.getName()).willReturn("pickupShipment");
        given(myJobDelivery.getName()).willReturn("deliverShipment");
        given(myJobPickup.getJob()).willReturn(myJob);
        given(myJobDelivery.getJob()).willReturn(myJob);
        given(myJob.getId()).willReturn("myJob");
        given(existingShipmentPickup.getJob()).willReturn(existingShipment);
        given(existingShipmentDelivery.getJob()).willReturn(existingShipment);
        given(existingShipment.getId()).willReturn("existingShipment");
        given(newShipmentPickup.getName()).willReturn("pickupShipment");
        given(newShipmentPickup.getJob()).willReturn(newShipment);
        given(newShipmentDelivery.getJob()).willReturn(newShipment);
        given(newShipment.getId()).willReturn("newShipment");
        tourActivities.add(myJobPickup);
        tourActivities.add(existingShipmentPickup);
        tourActivities.add(existingShipmentDelivery);
        tourActivities.add(myJobDelivery);

        Location myJobPickupLocation = Location.newInstance("myJob:pickup");
        Location myJobDeliveryLocation = Location.newInstance("myJob:delivery");
        Location existingShipmentPickupLocation = Location.newInstance("existingShipment:pickup");
        Location existingShipmentDeliveryLocation = Location.newInstance("existingShipment:delivery");
        Location newShipmentPickupLocation = Location.newInstance("newShipment:pickup");
        Location newShipmentDeliveryLocation = Location.newInstance("newShipment:delivery");
        given(myJobPickup.getLocation()).willReturn(myJobPickupLocation);
        given(myJobDelivery.getLocation()).willReturn(myJobDeliveryLocation);
        given(existingShipmentPickup.getLocation()).willReturn(existingShipmentPickupLocation);
        given(existingShipmentDelivery.getLocation()).willReturn(existingShipmentDeliveryLocation);
        given(newShipmentPickup.getLocation()).willReturn(newShipmentPickupLocation);
        given(newShipmentDelivery.getLocation()).willReturn(newShipmentDeliveryLocation);

        given(transportCostsMatrix.getTransportTime(eq(myJobPickupLocation), eq(myJobPickupLocation), eq(0.), eq(null), eq(null))).willReturn(0.);
        given(transportCostsMatrix.getTransportTime(eq(myJobPickupLocation), eq(existingShipmentPickupLocation), eq(0.), eq(null), eq(null))).willReturn(50.);
        given(transportCostsMatrix.getTransportTime(eq(existingShipmentPickupLocation), eq(existingShipmentPickupLocation), eq(0.), eq(null), eq(null))).willReturn(0.);
        given(transportCostsMatrix.getTransportTime(eq(newShipmentPickupLocation), eq(existingShipmentDeliveryLocation), eq(0.), eq(null), eq(null))).willReturn(30.);
        given(transportCostsMatrix.getTransportTime(eq(newShipmentPickupLocation), eq(newShipmentDeliveryLocation), eq(0.), eq(null), eq(null))).willReturn(100.);
        given(transportCostsMatrix.getTransportTime(eq(newShipmentDeliveryLocation), eq(existingShipmentDeliveryLocation), eq(0.), eq(null), eq(null))).willReturn(100.);
        given(transportCostsMatrix.getTransportTime(eq(existingShipmentDeliveryLocation), eq(myJobDeliveryLocation), eq(0.), eq(null), eq(null))).willReturn(30.);

        MaximumTimeInVehicleConstraint constraint = new MaximumTimeInVehicleConstraint(250., transportCostsMatrix, stateManager, "myJob");

        // when
        HardActivityConstraint.ConstraintsStatus constraintStatusPickup = constraint.fulfilled(context, existingShipmentPickup, newShipmentPickup, existingShipmentDelivery, 0.);

        // assert
        Assert.assertEquals(HardActivityConstraint.ConstraintsStatus.FULFILLED, constraintStatusPickup);

        // when
        HardActivityConstraint.ConstraintsStatus constraintStatusDropoff = constraint.fulfilled(context, newShipmentPickup, newShipmentDelivery, existingShipmentDelivery, 0.);

        // assert
        Assert.assertEquals(HardActivityConstraint.ConstraintsStatus.NOT_FULFILLED, constraintStatusDropoff);
    }
}
