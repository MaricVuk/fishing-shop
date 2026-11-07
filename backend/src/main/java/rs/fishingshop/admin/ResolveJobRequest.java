package rs.fishingshop.admin;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import rs.fishingshop.api.InputPatterns;

record ResolveJobRequest(@Size(max = 200) @Pattern(regexp = InputPatterns.SINGLE_LINE_TEXT) String reason) {
}
