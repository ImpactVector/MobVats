package net.impactvector.mobvats.render;

import java.util.*;
import com.google.gson.JsonParser;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.client.model.FeederArt;
import net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.VatMechanismRenderer;
import net.impactvector.mobvats.gamecontent.multiblock.common.client.FixtureModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.joml.Vector3f;
import net.minecraft.core.*;
import net.minecraft.world.phys.*;

/** CPU-only art ownership and continuity; physical topology is input, never output. */
public final class FeederArtGeometryTest {
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void verify(Map<String,Object> examples) throws Exception {
        require(FeederArt.RADIUS*16==1.5&&FeederArt.BODY_WIDTH*16==10,"Spindle/reel dimensions changed");
        var reels=new LinkedHashMap<String,Object>();
        for(var finish:List.of("master","t1","t2","t3")) {
            try(var input=FeederArtGeometryTest.class.getResourceAsStream("/assets/mobvats/models/block/procedural/"+finish+"/feed_port.json")) {
                var model=JsonParser.parseReader(new java.io.InputStreamReader(Objects.requireNonNull(input))).getAsJsonObject();
                require(model.getAsJsonObject("textures").asMap().values().stream().noneMatch(v->v.getAsString().contains("conduit_pipe_fluid")||v.getAsString().contains("controls")),"Feeder retains fixed resource/status accents");
                int nozzles=0,inlets=0,windings=0,plates=0,sockets=0;
                var bodyMin=new double[]{16,16,16};var bodyMax=new double[]{0,0,0};
                for(var value:model.getAsJsonArray("elements")) {
                    var e=value.getAsJsonObject();var faces=e.getAsJsonObject("faces");
                    for(var face:faces.entrySet())if(face.getValue().getAsJsonObject().get("texture").getAsString().equals("#socket"))sockets++;
                    if(faces.getAsJsonObject("south").get("texture").getAsString().equals("#nozzle"))nozzles++;
                    String material=faces.getAsJsonObject("east").get("texture").getAsString();
                    if(material.equals("#connection")) {
                        inlets++;
                        require(e.getAsJsonArray("from").get(0).getAsDouble()==13&&e.getAsJsonArray("to").get(0).getAsDouble()==16,"Spindle does not extend to adjacent conduit");
                        require(e.getAsJsonArray("from").get(1).getAsDouble()==6.5&&e.getAsJsonArray("to").get(1).getAsDouble()==9.5,"Construction inlet has wrong mating diameter");
                    } else {
                        for(int a=0;a<3;a++) {
                            double from=e.getAsJsonArray("from").get(a).getAsDouble(),to=e.getAsJsonArray("to").get(a).getAsDouble();
                            require(from>=3&&to<=13,"Reel body is outside centered 10px cube");
                            bodyMin[a]=Math.min(bodyMin[a],from);bodyMax[a]=Math.max(bodyMax[a],to);
                        }
                        double x=e.getAsJsonArray("from").get(0).getAsDouble(),X=e.getAsJsonArray("to").get(0).getAsDouble();
                        require(e.getAsJsonArray("from").get(1).getAsDouble()==3&&e.getAsJsonArray("to").get(1).getAsDouble()==13&&e.getAsJsonArray("from").get(2).getAsDouble()==3&&e.getAsJsonArray("to").get(2).getAsDouble()==13,"Opaque reel section does not fill its cross-section");
                        if(material.equals("#hose")){windings++;require(x==4&&X==12,"Winding must fill the space between end plates");}
                        else {plates++;require((x==3&&X==4)||(x==12&&X==13),"End plate is not a closed 1px cap");require(faces.getAsJsonObject(x==3?"west":"east").get("texture").getAsString().equals("#socket"),"Outer cap lacks central socket texture");}
                    }
                }
                require(nozzles==0&&inlets==1&&windings==1&&plates==2&&sockets==2,"Missing solid winding, closed end caps or conduit sockets");
                for(int a=0;a<3;a++)require(bodyMin[a]==3&&bodyMax[a]==13&&bodyMax[a]-bodyMin[a]==10,"Generated reel is not exactly 10x10x10");
                if(finish.equals("t1"))for(var inlet:Direction.values())reels.put(inlet.getSerializedName(),reelMesh(model,inlet));
            }
            try(var input=FeederArtGeometryTest.class.getResourceAsStream("/assets/mobvats/textures/block/procedural/"+finish+"/feeder_hose.png")) {
                var image=javax.imageio.ImageIO.read(Objects.requireNonNull(input));
                require(image.getRGB(3,8)!=image.getRGB(4,8)&&image.getRGB(3,8)==image.getRGB(6,8)&&image.getRGB(4,2)==image.getRGB(4,13),"Winding texture lacks continuous repeated ribs");
                for(int y=0;y<16;y++)for(int x=0;x<16;x++){require((image.getRGB(x,y)>>>24)==255,"Winding texture has transparent gaps");require((image.getRGB(x,y)&0xffffff)!=0x168bfa,"Fixed azure accent returned");}
            }
            try(var input=FeederArtGeometryTest.class.getResourceAsStream("/assets/mobvats/textures/block/procedural/"+finish+"/feeder_socket.png")) {
                var image=javax.imageio.ImageIO.read(Objects.requireNonNull(input));
                require((image.getRGB(8,8)&0xffffff)==0x141414&&image.getRGB(2,2)!=image.getRGB(8,8),"Closed cap lacks a black central socket");
                for(int y=0;y<16;y++)for(int x=0;x<16;x++)require((image.getRGB(x,y)>>>24)==255,"End cap has transparent pixels");
            }
        }
        examples.put("feeder_reels",reels);
        var scenes=new ArrayList<Object>();
        var min=BlockPos.ZERO;var max=new BlockPos(6,4,6);
        for(var face:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)) {
            var port=switch(face){case NORTH->new BlockPos(3,1,0);case SOUTH->new BlockPos(3,1,6);case WEST->new BlockPos(0,1,3);default->new BlockPos(6,1,3);};
            var tangent=face.getAxis()==Direction.Axis.Z?Direction.EAST:Direction.NORTH;
            var geometry=geometry(min,max,port,port.relative(tangent),ConduitCladding.CASING);
            var before=geometry.snapshot().save();int occupied=geometry.occupied().size();
            var terminals=FeederArt.terminals(geometry);
            require(terminals.size()==1,"Wall Feeder duplicated terminal");
            var terminal=terminals.getFirst();var endpoint=terminal.endpoint();
            require(endpoint.inward()==face.getOpposite(),"Feeder orientation differs from physical service mount");
            require(terminal.bridge().stream().anyMatch(b->b.inflate(1e-7).contains(endpoint.serviceConnectionAnchor())),"Feeder bridge misses physical conduit anchor");
            require(terminal.inlet()==tangent&&FeederArt.inlet(geometry,port).orElseThrow()==tangent,"Spindle does not align with adjacent conduit");
            require(terminal.bridge().stream().anyMatch(b->b.inflate(1e-7).contains(FeederArt.spindleAttachment(endpoint,terminal.inlet()))),"Connection misses reel spindle");
            require(terminal.bridge().stream().allMatch(b->Math.max(b.getXsize(),Math.max(b.getYsize(),b.getZsize()))<=.750001),"Spindle connection exceeds local fitting");
            require(occupied==geometry.occupied().size()&&before.equals(geometry.snapshot().save()),"Decorative geometry changed occupied volumes/topology");
            require(FeederArt.terminals(ConduitGeometry.bake(ConduitGeometry.Snapshot.load(before))).equals(terminals),"Restoration changed terminal anchors");
            var cow=switch(face){case NORTH->List.of(2,0,0);case SOUTH->List.of(2,0,4);case WEST->List.of(0,0,2);default->List.of(4,0,2);};
            scenes.add(scene(face.getSerializedName()+" wall",geometry,port,face,false,List.of(cow)));
        }
        var port=new BlockPos(3,1,3);var tap=port.below();
        var shared=geometry(min,max,port,tap,ConduitCladding.CASING);
        var feeds=new ArrayList<VatPresentation.Link>();
        var cows=List.of(List.of(1,0,1),List.of(3,0,1),List.of(1,0,3),List.of(3,0,3));
        for(var cow:cows) {
            var body=new AABB(1.05+cow.get(0),1,1.05+cow.get(2),1.95+cow.get(0),2.4,1.95+cow.get(2));
            var target=net.impactvector.mobvats.gamecontent.mob.MobRenderDefinition.DEFAULT.headAnchor(body,Direction.NORTH);
            feeds.add(new VatPresentation.Link(port,Vec3.atCenterOf(port),target,Direction.NORTH,true));
        }
        require(FeederArt.terminals(shared).size()==1&&feeds.size()==4,"Four-cow setup duplicated feeding hardware");
        require(VatMechanismRenderer.bake(new VatPresentation(List.of(),feeds)).hoses().size()==4,"Four-cow feeding hoses were lost/duplicated");
        require(feeds.stream().allMatch(l->VatInteractionGeometry.feedCurve(l).points().getFirst().equals(l.base())),"Feeder art moved physical feeding routes");
        scenes.add(scene("four cows / one centered hose reel",shared,port,Direction.SOUTH,true,cows));
        var fluid=new ConduitGeometry.Cell(port.east(),ConduitResource.FLUID,ConduitCladding.BARE,ConduitMode.TAP,0,List.of(new ConduitGeometry.Attachment(port,EndpointGeometry.Kind.FEED)));
        var item=new ConduitGeometry.Cell(port.west(),ConduitResource.ITEM,ConduitCladding.BARE,ConduitMode.TAP,0,List.of(new ConduitGeometry.Attachment(port,EndpointGeometry.Kind.FEED)));
        var both=ConduitGeometry.bake(new ConduitGeometry.Snapshot(min,max,List.of(fluid,item)));
        var terminal=FeederArt.terminals(both).getFirst();
        require(FeederArt.terminals(both).size()==1,"Item/slurry services duplicated nozzle");
        for(var fitting:both.endpointFittings())require(terminal.bridge().stream().anyMatch(b->b.inflate(1e-7).contains(fitting.endpoint().serviceConnectionAnchor())),"Terminal missed an associated service fitting");
        require(FeederArt.terminals(ConduitGeometry.Geometry.EMPTY).isEmpty(),"Disconnected Feeder invented a physical supply");
        examples.put("feeder_scenes",scenes);
        System.out.println("PASS: centered 10x10x10 reel, no panel/perforations, conduit-aligned spindle, restored four-cow hoses, unchanged routes and restoration.");
    }
    private static ConduitGeometry.Geometry geometry(BlockPos min,BlockPos max,BlockPos port,BlockPos tap,ConduitCladding cladding) {
        var cell=new ConduitGeometry.Cell(tap,ConduitResource.FLUID,cladding,ConduitMode.TAP,0,List.of(new ConduitGeometry.Attachment(port,EndpointGeometry.Kind.FEED)));
        return ConduitGeometry.bake(new ConduitGeometry.Snapshot(min,max,List.of(cell)));
    }
    private static Map<String,Object> scene(String name,ConduitGeometry.Geometry geometry,BlockPos port,Direction facing,boolean interior,List<List<Integer>> cows) {
        var volumes=new ArrayList<Object>();
        geometry.pipeSegments().forEach(v->volumes.add(box(v.bounds(),v.material()==ConduitGeometry.Material.COLLAR?"collar":"network")));
        geometry.endpointFittings().forEach(f->f.volumes().forEach(v->volumes.add(box(v.bounds(),"pipe"))));
        var terminal=FeederArt.terminals(geometry).getFirst();terminal.bridge().forEach(b->volumes.add(box(b,"pipe")));
        var feeds=new ArrayList<VatPresentation.Link>();
        for(var cow:cows) {
            var body=new AABB(1.05+cow.get(0),1,1.05+cow.get(2),1.95+cow.get(0),2.4,1.95+cow.get(2));
            var target=net.impactvector.mobvats.gamecontent.mob.MobRenderDefinition.DEFAULT.headAnchor(body,Direction.NORTH);
            feeds.add(new VatPresentation.Link(port,terminal.endpoint().interactionOrigin().orElseThrow(),target,terminal.endpoint().inward(),true));
        }
        var hoses=VatMechanismRenderer.bake(new VatPresentation(List.of(),feeds)).hoses();
        require(hoses.size()==cows.size(),"Missing per-mob feeding hose");var quads=new ArrayList<Object>();
        for(int i=0;i<hoses.size();i++) {
            var expected=net.impactvector.mobvats.gamecontent.multiblock.vat.client.render.CableRenderer.bake(VatInteractionGeometry.feedCurve(feeds.get(i)),VatInteractionGeometry.FEED_WIDTH);
            require(hoses.get(i).equals(expected),"Hose differs from authoritative curve/thickness");
            for(var q:hoses.get(i).quads())quads.add(Map.of("points",List.of(q.a(),q.b(),q.c(),q.d()).stream().map(p->List.of(p.x*16,p.y*16,p.z*16)).toList(),"faces",Map.of("mesh",Map.of("texture","#hose"))));
        }
        return Map.of("name",name,"port",List.of(port.getX(),port.getY(),port.getZ()),"inlet",terminal.inlet().getSerializedName(),"volumes",volumes,"cows",cows,"hoses",quads);
    }
    private static List<Object> reelMesh(com.google.gson.JsonObject model,Direction inlet) {
        var faceIndices=Map.of("down",new int[]{0,1,5,4},"up",new int[]{3,7,6,2},"north",new int[]{0,3,2,1},"south",new int[]{4,5,6,7},"west",new int[]{0,4,7,3},"east",new int[]{1,2,6,5});
        var mesh=new ArrayList<Object>();boolean attached=false;
        for(var value:model.getAsJsonArray("elements")) {
            var e=value.getAsJsonObject();var f=e.getAsJsonArray("from");var t=e.getAsJsonArray("to");
            float x=f.get(0).getAsFloat()/16,y=f.get(1).getAsFloat()/16,z=f.get(2).getAsFloat()/16,X=t.get(0).getAsFloat()/16,Y=t.get(1).getAsFloat()/16,Z=t.get(2).getAsFloat()/16;
            var v=new Vector3f[]{new Vector3f(x,y,z),new Vector3f(X,y,z),new Vector3f(X,Y,z),new Vector3f(x,Y,z),new Vector3f(x,y,Z),new Vector3f(X,y,Z),new Vector3f(X,Y,Z),new Vector3f(x,Y,Z)};
            var cube=new ArrayList<Vec3>();boolean connection=false;
            for(var face:e.getAsJsonObject("faces").entrySet()) {
                var ix=faceIndices.get(face.getKey());var seed=FixtureModel.withPoints(new BakedQuad(new int[32],-1,Direction.NORTH,null,true),new Vector3f[]{v[ix[0]],v[ix[1]],v[ix[2]],v[ix[3]]});
                var q=FeederArt.orientInlet(seed,inlet);var vertices=q.getVertices();var points=new ArrayList<Object>();
                for(int i=0;i<4;i++){var p=new Vec3(Float.intBitsToFloat(vertices[i*8]),Float.intBitsToFloat(vertices[i*8+1]),Float.intBitsToFloat(vertices[i*8+2]));cube.add(p);points.add(List.of(p.x*16,p.y*16,p.z*16));}
                String material=face.getValue().getAsJsonObject().get("texture").getAsString();connection=material.equals("#connection");
                mesh.add(Map.of("points",points,"faces",Map.of("mesh",Map.of("texture",material)),"construction",connection));
            }
            if(!connection) {
                var bounds=new AABB(cube.stream().mapToDouble(p->p.x).min().orElseThrow(),cube.stream().mapToDouble(p->p.y).min().orElseThrow(),cube.stream().mapToDouble(p->p.z).min().orElseThrow(),cube.stream().mapToDouble(p->p.x).max().orElseThrow(),cube.stream().mapToDouble(p->p.y).max().orElseThrow(),cube.stream().mapToDouble(p->p.z).max().orElseThrow());
                attached|=bounds.inflate(1e-7).contains(new Vec3(.5,.5,3.16/16));
            }
        }
        require(attached,"Feeding hose origin floats away from reel for "+inlet);return mesh;
    }
    private static Map<String,Object> box(AABB b,String material){return Map.of("from",List.of(b.minX*16,b.minY*16,b.minZ*16),"to",List.of(b.maxX*16,b.maxY*16,b.maxZ*16),"material",material);}
}
