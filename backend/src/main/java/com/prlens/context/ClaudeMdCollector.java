package com.prlens.context;

import com.prlens.model.ChangedFile;
import com.prlens.model.ContextFile;
import java.util.List;

final class ClaudeMdCollector {

  private final BaseFileReader reader;

  ClaudeMdCollector(BaseFileReader reader) {
    this.reader = reader;
  }

  List<ContextFile> collect(List<ChangedFile> targets) {
    return null;
  }
}
