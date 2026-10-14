package com.energym.app.domain;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class MetricValidatorTest {
    private final MetricValidator validator=new MetricValidator();
    private Map<String,String> values(String... entries){Map<String,String> m=new LinkedHashMap<>();for(int i=0;i<entries.length;i+=2)m.put(entries[i],entries[i+1]);return m;}
    @Test public void strengthAllowsZeroLoadAndCommaDecimal(){assertTrue(validator.validate(MetricType.STRENGTH,values("sets","2","repetitions","8","loadKg","0","restSeconds","30","effort","6,5")).isEmpty());}
    @Test public void strengthRejectsEmptySets(){assertFalse(validator.validate(MetricType.STRENGTH,values("sets","","repetitions","8","loadKg","0","restSeconds","30")).isEmpty());}
    @Test public void strengthRejectsFractionalRepetitionsAndNegativeLoad(){assertFalse(validator.validate(MetricType.STRENGTH,values("sets","2","repetitions","2,5","loadKg","-1","restSeconds","30")).isEmpty());}
    @Test public void isometricRequiresPositiveDuration(){assertFalse(validator.validate(MetricType.ISOMETRIC,values("sets","2","durationSeconds","0","restSeconds","0")).isEmpty());}
    @Test public void enduranceAcceptsCommaDistance(){assertTrue(validator.validate(MetricType.ENDURANCE,values("timeMinutes","12,5","intervals","1","distanceKm","2,8")).isEmpty());}
    @Test public void enduranceRejectsNegativeDistance(){assertFalse(validator.validate(MetricType.ENDURANCE,values("timeMinutes","12","intervals","1","distanceKm","-1")).isEmpty());}
    @Test public void mobilityRequiresKnownSide(){assertFalse(validator.validate(MetricType.MOBILITY,values("durationSeconds","20","repetitions","4","side","ninguno")).isEmpty());assertTrue(validator.validate(MetricType.MOBILITY,values("durationSeconds","20","repetitions","4","side","izquierdo")).isEmpty());}
    @Test public void practiceRequiresBlocks(){assertFalse(validator.validate(MetricType.SPORT_PRACTICE,values("blocks","0","durationMinutes","15")).isEmpty());assertTrue(validator.validate(MetricType.SPORT_PRACTICE,values("blocks","2","durationMinutes","15")).isEmpty());}
    @Test public void emptySessionMetricIsInvalidForEveryType(){for(MetricType type:MetricType.values())assertFalse(type.name(),validator.validate(type,Collections.emptyMap()).isEmpty());}
    @Test public void nonFiniteAndOutOfRangeEffortFail(){assertFalse(validator.validate(MetricType.ENDURANCE,values("timeMinutes","NaN","intervals","1","effort","11")).isEmpty());}
}
