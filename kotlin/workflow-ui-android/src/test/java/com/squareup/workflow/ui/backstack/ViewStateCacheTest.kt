package com.squareup.workflow.ui.backstack

import android.os.Bundle
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ViewStateCacheTest {

  @Test fun fnord() {
    val b = Bundle()
    b.putString("able", "baker")
    assertThat(b.getString("able")).isEqualTo("baker")
  }
}
