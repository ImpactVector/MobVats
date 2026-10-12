package net.impactvector.mobvats.render;

import java.util.List;
import java.util.Map;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.client.render.MixerControllerRenderer;
import net.minecraft.core.BlockPos;

/** CPU-only verification of the actual formed Shaft mesh; no client or world is launched. */
public final class MixerShaftGeometryTest {
    public static void main(String[] args) throws Exception {
        net.neoforged.fml.loading.LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        verifyChunkOwnership();
        for(int diameter:List.of(3,5,7,9))for(int reduction=0;MixerVesselGeometry.reduced(diameter,reduction).isPresent();reduction++)
            for(int depth:List.of(1,8))for(int inset:List.of(0,MixerVesselGeometry.SHELL_DEPTH_PIXELS)){
                var a=new MixerAssembly(BlockPos.ZERO,depth,diameter,reduction,inset);
                var d=MixerRenderDescriptor.from(a);var mesh=MixerControllerRenderer.bakeShaft(d);
                double minX=1,maxX=-1,minZ=1,maxZ=-1,minY=1,maxY=-10;
                for(var quad:mesh.quads())for(var p:List.of(quad.a(),quad.b(),quad.c(),quad.d())){
                    minX=Math.min(minX,p.x);maxX=Math.max(maxX,p.x);minZ=Math.min(minZ,p.z);maxZ=Math.max(maxZ,p.z);
                    minY=Math.min(minY,p.y);maxY=Math.max(maxY,p.y);
                    if(!a.vessel().contains(p.x,p.z) || p.y<d.shaftBottom().y || p.y>0)throw new AssertionError("Shaft mesh leaves vessel");
                }
                if(minX!=-.1875 || maxX!=.1875 || minZ!=-.1875 || maxZ!=.1875 || minY!=d.shaftBottom().y || maxY!=0)
                    throw new AssertionError("Formed Shaft is not centered six-pixel square at installed depth");
                for(var q:mesh.quads())if(q.normal().y==0 && (q.a().y!=q.b().y || q.c().y!=q.d().y || q.v1()<=q.v0()))
                    throw new AssertionError("Shaft V axis must follow its longitudinal Y axis");
                if(a.slotCount()!=(diameter-2*reduction-1)*(diameter-2*reduction-1)*depth/8)
                    throw new AssertionError("Art changed simplified capacity");
                for(var paddle:MixerControllerRenderer.bakePaddles(d))for(var q:paddle.quads())for(var p:List.of(q.a(),q.b(),q.c(),q.d())) {
                    double radial=Math.sqrt(p.x*p.x+p.z*p.z);
                    if(radial>d.assembly().vessel().faceRadius()-MixerRenderDescriptor.WALL_THICKNESS)
                        throw new AssertionError("Paddle intersects a wall during rotation");
                }
            }
        if(args.length>0) {
            var examples=new java.util.LinkedHashMap<String,Object>();
            for(int diameter:List.of(3,5,9)) {
                var d=MixerRenderDescriptor.from(new MixerAssembly(BlockPos.ZERO,3,diameter,0));
                var parts=new java.util.ArrayList<Object>();
                for(var face:d.walls())parts.add(Map.of("points",face.vertices().stream().map(MixerShaftGeometryTest::pixel).toList(),"material","vessel"));
                for(var q:MixerControllerRenderer.bakeShaft(d).quads())parts.add(quad(q,"shaft"));
                for(var paddle:MixerControllerRenderer.bakePaddles(d))for(var q:paddle.quads())parts.add(quad(q,"paddle"));
                examples.put("mixer_"+diameter,parts);
            }
            var path=java.nio.file.Path.of(args[0]);java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(examples));
        }
        System.out.println("PASS: actual formed Shaft mesh is centered, six pixels wide and within every legal vessel/depth/floor envelope.");
    }
    private static List<Double> pixel(net.minecraft.world.phys.Vec3 p){return List.of(p.x*16+8,p.y*16,p.z*16+8);}
    private static void verifyChunkOwnership(){
        var preview=List.of(new net.minecraft.client.renderer.block.model.BakedQuad(new int[32],-1,net.minecraft.core.Direction.UP,null,false));
        var original=(net.minecraft.client.resources.model.BakedModel)java.lang.reflect.Proxy.newProxyInstance(
            net.minecraft.client.resources.model.BakedModel.class.getClassLoader(),new Class<?>[]{net.minecraft.client.resources.model.BakedModel.class},
            (proxy,method,args)->{if(method.getName().equals("getQuads"))return preview;throw new AssertionError("Unexpected model call");});
        var shaft=new net.impactvector.mobvats.gamecontent.multiblock.mixer.client.model.MixerShaftModel(original);
        var random=net.minecraft.util.RandomSource.create();
        for(boolean formed:new boolean[]{false,true,false,true,false}) {
            var data=net.neoforged.neoforge.client.model.data.ModelData.EMPTY.derive()
                .with(net.impactvector.mobvats.gamecontent.multiblock.mixer.part.MixerShaftEntity.FORMED,formed).build();
            for(var side:net.minecraft.core.Direction.values()) {
                var quads=shaft.getQuads(null,side,random,data,null);
                if(formed?!quads.isEmpty():quads!=preview)throw new AssertionError("Duplicate formed Shaft or missing construction geometry");
            }
        }
        if(shaft.getQuads(null,null,random,net.neoforged.neoforge.client.model.data.ModelData.EMPTY,null)!=preview)
            throw new AssertionError("Initial or inventory Shaft geometry missing");
    }
    private static Object quad(net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.CableRenderer.Quad q,String material){
        return Map.of("points",List.of(pixel(q.a()),pixel(q.b()),pixel(q.c()),pixel(q.d())),"material",material,"uv",List.of(0,q.v0()*16,16,q.v1()*16));
    }
}
