package org.stepic.droid.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.stepic.droid.testUtils.TestingGsonProvider
import org.stepik.android.model.Section

@RunWith(RobolectricTestRunner::class)
class SectionTest {

    private val gson: Gson = TestingGsonProvider.gson

    @Test
    fun nullSectionRequirementJson() {
        //"required_section": null
        val sectionJson = """
            {"id": 8761, "course": 4100, "units": [], "position": 2, "discounting_policy": "no_discount", "progress": "79-8761", "actions": {"test_section": "#"}, "required_section": null, "required_percent": 100, "is_requirement_satisfied": true, "is_exam": false, "exam_duration_minutes": 120, "exam_session": null, "proctor_session": null, "description": "", "title": "\u041d\u043e\u0432\u044b\u0439 \u043c\u043e\u0434\u0443\u043b\u044c2", "slug": "\u041d\u043e\u0432\u044b\u0439-\u043c\u043e\u0434\u0443\u043b\u044c2-8761", "begin_date": null, "end_date": null, "soft_deadline": null, "hard_deadline": null, "grading_policy": "halved", "begin_date_source": null, "end_date_source": null, "soft_deadline_source": null, "hard_deadline_source": null, "grading_policy_source": null, "is_active": true, "create_date": "2017-10-20T15:27:21Z", "update_date": "2017-10-20T15:27:49Z"}
            """

        val result = gson.fromJson(sectionJson, Section::class.java)
        assertTrue(result.isRequirementSatisfied)
        assertEquals(0, result.requiredSection)
    }

}
