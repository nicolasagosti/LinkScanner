package com.linkscanner.entities;

/**
 * Una señal detectada en el link.
 *
 * @param code identificador estable, pensado para la app
 * @param message explicación para mostrarle al usuario
 * @param weight cuánto suma al puntaje de riesgo
 */
public record Finding(String code, String message, int weight) {
}
