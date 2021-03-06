package io.github.emergentorganization.emergent2dcore.systems;

import com.artemis.Aspect;
import com.artemis.BaseEntitySystem;
import com.artemis.ComponentMapper;
import com.artemis.annotations.Profile;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import io.github.emergentorganization.cellrpg.tools.profiling.EmergentProfiler;
import io.github.emergentorganization.emergent2dcore.components.Position;
import io.github.emergentorganization.emergent2dcore.components.Rotation;
import io.github.emergentorganization.emergent2dcore.components.Scale;
import io.github.emergentorganization.emergent2dcore.components.Visual;
import io.github.emergentorganization.cellrpg.managers.AssetManager;
import io.github.emergentorganization.cellrpg.tools.postprocessing.TronShader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;


@Wire
@Profile(using=EmergentProfiler.class, enabled=true)
public class RenderSystem extends BaseEntitySystem {
    private final Logger logger = LogManager.getLogger(getClass());

    private final TextureRegion fboRegion;
    private final FrameBuffer frameBuffer;
    private final SpriteBatch batch;
    private final LinkedList<Integer> sortedEntityIds;
    private TronShader tronShader;
    private ComponentMapper<Visual> vm;
    private ComponentMapper<Position> pm;
    private ComponentMapper<Scale> sm;
    private ComponentMapper<Rotation> rm;
    private CameraSystem cameraSystem;
    private AssetManager assetManager; // being a registered system, it is injected on runtime
    private boolean tronShaderEnabled = false;
    private Batch outBatch;

    // list of particleEffects with no parent entity
    private ArrayList<ParticleEffect> particleEffects = new ArrayList<ParticleEffect>();

    public RenderSystem(SpriteBatch batch) {
        super(Aspect.all(Position.class, Rotation.class, Scale.class, Visual.class));  // TODO: .one(Visual.class, Particles.class)

        this.batch = batch;
        this.outBatch = new SpriteBatch();
        sortedEntityIds = new LinkedList<Integer>();
        frameBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
        Texture cb = frameBuffer.getColorBufferTexture();
        fboRegion = new TextureRegion(cb, 0, 0, cb.getWidth(), cb.getHeight());
        fboRegion.flip(false, true); // FBO uses lower left, TextureRegion uses upper-left
    }

    public void registerOrphanParticleEffect(ParticleEffect effect){
        // registers an (entity-parent)-less particle effect for drawing
        particleEffects.add(effect);
    }

    @Override
    protected void begin() {
        frameBuffer.begin();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(cameraSystem.getGameCamera().combined);
        batch.begin();
    }

    @Override
    protected void processSystem() {
        logger.trace("render dt: " + world.getDelta());

        // render entities:
        for (Integer id : sortedEntityIds) {
            process(id);
        }

        // render non-entity particle effects:
        ArrayList<ParticleEffect> delQueue = new ArrayList<ParticleEffect>();
        logger.debug("rendering " + particleEffects.size() + " orphan particle effects");
        for (ParticleEffect p : particleEffects){
            if(p.isComplete()){
                p.dispose();
                delQueue.add(p);
            } else {
                p.update(world.getDelta());
                p.draw(batch);
            }
        }
        logger.trace(delQueue.size() + " particle effects complete; removing.");
        for (ParticleEffect dp : delQueue){
            particleEffects.remove(dp);
        }
    }

    protected void process(int entityId) {
        Visual v = vm.get(entityId);
        Position p = pm.get(entityId);
        Scale s = sm.get(entityId);
        Rotation r = rm.get(entityId);

        TextureRegion t = assetManager.getCurrentRegion(v);
        if (t != null) {
            if (v.isAnimation) {
                v.stateTime += world.getDelta();
            }
            batch.draw(t, p.position.x, p.position.y, 0, 0, t.getRegionWidth(), t.getRegionHeight(), s.scale, s.scale, r.angle);
        }
    }

    @Override
    protected void end() {
        batch.end();
        frameBuffer.end();

        if (tronShaderEnabled) {
            tronShader.render(frameBuffer);
        }

        // Render final texture to screen
        outBatch.begin();
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        outBatch.draw(fboRegion, 0, 0);
        outBatch.end();
    }

    @Override
    protected void inserted(int entityId) {
        sortedEntityIds.add(entityId);
        Collections.sort(sortedEntityIds, new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                Visual v1 = vm.get(o1);
                Visual v2 = vm.get(o2);
                return v1.index.ordinal() - v2.index.ordinal();
            }
        });
    }

    @Override
    protected void removed(int entityId) {
        ListIterator<Integer> iter = sortedEntityIds.listIterator();

        while (iter.hasNext()) {
            Integer id = iter.next();
            if (id - entityId == 0) {
                iter.remove();
            }
        }
    }

    public List<Integer> getSortedEntityIds() {
        return Collections.unmodifiableList(sortedEntityIds);
    }

    /**
     * Enables the Tron glow shader
     *
     * @return The RenderSystem for shader chaining
     */
    public RenderSystem setTronShader(TronShader tronShader) {
        this.tronShader = tronShader;
        this.tronShaderEnabled = this.tronShader != null;
        return this;
    }
}
