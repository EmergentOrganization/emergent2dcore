package io.github.emergentorganization.emergent2dcore.components;

import com.artemis.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Required for all entities.
 * Component which contains variables that define how the entity is constructed/deconstructed
 * as the player travels around the world.
 */
public class Lifecycle extends Component {
    // maximum distance from player before being deconstructed (can be set to -1 for infinite distance)
    public float maxPlayerDist = -1f;
    public boolean manualKill = false;  // set this to true to kill the entity

    private final Logger logger = LogManager.getLogger(getClass());

    public void kill(){
        // kills the entity
        logger.info("manually killing entity");
        manualKill = true;
    }
}
