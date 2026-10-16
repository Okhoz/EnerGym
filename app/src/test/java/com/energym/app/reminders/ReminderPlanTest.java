package com.energym.app.reminders;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.EnumSet;

import static org.junit.Assert.*;

public class ReminderPlanTest {
	// 2026-10-16 es viernes.
	private static final LocalDateTime FRIDAY_NOON = LocalDateTime.of(2026, 10, 16, 12, 0);

	@Test
	public void trainingDaysAreSpreadAcrossTheWeek() {
		assertEquals(EnumSet.noneOf(DayOfWeek.class), ReminderPlan.trainingDays(0));
		assertEquals(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), ReminderPlan.trainingDays(3));
		for (int days = 1; days <= 7; days++)
			assertEquals(days, ReminderPlan.trainingDays(days).size());
		assertEquals("lun, mié, vie", ReminderPlan.defaults().withDaysPerWeek(3).daysText());
	}

	@Test
	public void trainingReminderFiresOnTheNextTrainingDayAtTheChosenTime() {
		ReminderPlan plan = ReminderPlan.defaults().withTraining(true).withDaysPerWeek(3).withTrainingMinute(18 * 60 + 30);
		assertEquals("Hoy viernes, más tarde", LocalDateTime.of(2026, 10, 16, 18, 30), plan.nextTraining(FRIDAY_NOON));
		assertEquals("Pasada la hora, el lunes siguiente", LocalDateTime.of(2026, 10, 19, 18, 30),
			plan.nextTraining(LocalDateTime.of(2026, 10, 16, 18, 30)));
		assertEquals("18:30", plan.trainingTimeText());
	}

	@Test
	public void trainingReminderNeedsSwitchAndPlan() {
		assertNull(ReminderPlan.defaults().withDaysPerWeek(3).nextTraining(FRIDAY_NOON));
		assertNull("Sin plan no hay días que avisar", ReminderPlan.defaults().withTraining(true).nextTraining(FRIDAY_NOON));
	}

	@Test
	public void waterRemindersStayInsideTheDaytimeWindow() {
		ReminderPlan plan = ReminderPlan.defaults().withWater(true).withWaterHours(2);
		assertEquals(LocalDateTime.of(2026, 10, 16, 13, 0), plan.nextWater(FRIDAY_NOON));
		assertEquals("Antes de las 9 espera a la mañana", LocalDateTime.of(2026, 10, 16, 9, 0),
			plan.nextWater(LocalDateTime.of(2026, 10, 16, 6, 15)));
		assertEquals("Tras el último aviso, mañana a las 9", LocalDateTime.of(2026, 10, 17, 9, 0),
			plan.nextWater(LocalDateTime.of(2026, 10, 16, 21, 0)));
		assertEquals(LocalDateTime.of(2026, 10, 16, 15, 0),
			plan.withWaterHours(3).nextWater(FRIDAY_NOON));
		assertNull(ReminderPlan.defaults().nextWater(FRIDAY_NOON));
	}

	@Test
	public void valuesAreClampedToSaneRanges() {
		ReminderPlan plan = new ReminderPlan(true, 25 * 60, 9, true, 12);
		assertEquals(60, plan.trainingMinute);
		assertEquals(7, plan.daysPerWeek);
		assertEquals(4, plan.waterHours);
	}
}
