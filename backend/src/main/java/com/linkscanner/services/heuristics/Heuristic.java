package com.linkscanner.services.heuristics;

import java.util.Optional;

import com.linkscanner.entities.Finding;
import com.linkscanner.entities.ParsedUrl;

/**
 * Una regla de detección. Para sumar una nueva alcanza con crear otro {@code @Component} que
 * implemente esta interfaz.
 */
public interface Heuristic {

	Optional<Finding> evaluate(ParsedUrl url);

}
