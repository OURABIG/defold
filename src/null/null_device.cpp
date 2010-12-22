#include <string.h>
#include <assert.h>
#include <vectormath/cpp/vectormath_aos.h>

#include "../graphics_device.h"
#include "null_device.h"

using namespace Vectormath::Aos;

namespace dmGraphics
{
    uint16_t TYPE_SIZE[] =
    {
        sizeof(char), // TYPE_BYTE
        sizeof(unsigned char), // TYPE_UNSIGNED_BYTE
        sizeof(short), // TYPE_SHORT
        sizeof(unsigned short), // TYPE_UNSIGNED_SHORT
        sizeof(int), // TYPE_INT
        sizeof(unsigned int), // TYPE_UNSIGNED_INT
        sizeof(float) // TYPE_FLOAT
    };

    uint16_t TEXTURE_FORMAT_SIZE[] =
    {
        1, // TEXTURE_FORMAT_LUMINANCE
        3, // TEXTURE_FORMAT_RGB
        4, // TEXTURE_FORMAT_RGBA
        3, // TEXTURE_FORMAT_RGB_DXT1
        4, // TEXTURE_FORMAT_RGBA_DXT1
        4, // TEXTURE_FORMAT_RGBA_DXT3
        4 // TEXTURE_FORMAT_RGBA_DXT5
    };

    Device gdevice;
    Context gcontext;

    HContext GetContext()
    {
        return (HContext)&gcontext;
    }

    HDevice NewDevice(int* argc, char** argv, CreateDeviceParams *params )
    {
        assert(params);
        memset(gdevice.m_VertexStreams, 0, sizeof(gdevice.m_VertexStreams));
        memset(gdevice.m_VertexProgramRegisters, 0, sizeof(gdevice.m_VertexProgramRegisters));
        memset(gdevice.m_FragmentProgramRegisters, 0, sizeof(gdevice.m_FragmentProgramRegisters));
        gdevice.m_DisplayWidth = params->m_DisplayWidth;
        gdevice.m_DisplayHeight = params->m_DisplayHeight;
        gdevice.m_Opened = 1;
        gdevice.m_FrameBuffer.m_ColorBuffer = new char[4 * gdevice.m_DisplayWidth * gdevice.m_DisplayHeight];
        gdevice.m_FrameBuffer.m_DepthBuffer = new char[4 * gdevice.m_DisplayWidth * gdevice.m_DisplayHeight];
        gdevice.m_FrameBuffer.m_AccumBuffer = new char[4 * gdevice.m_DisplayWidth * gdevice.m_DisplayHeight];
        gdevice.m_FrameBuffer.m_StencilBuffer = new char[4 * gdevice.m_DisplayWidth * gdevice.m_DisplayHeight];
        gdevice.m_RenderBuffer = &gdevice.m_FrameBuffer;
        gdevice.m_VertexProgram = 0x0;
        gdevice.m_FragmentProgram = 0x0;
        return (HDevice)&gdevice;
    }

    void DeleteDevice(HDevice device)
    {
        gdevice.m_Opened = 0;
        delete [] (char*)gdevice.m_FrameBuffer.m_ColorBuffer;
        delete [] (char*)gdevice.m_FrameBuffer.m_DepthBuffer;
        delete [] (char*)gdevice.m_FrameBuffer.m_AccumBuffer;
        delete [] (char*)gdevice.m_FrameBuffer.m_StencilBuffer;
        for (uint32_t i = 0; i < MAX_VERTEX_STREAM_COUNT; ++i)
        {
            VertexStream& vs = gdevice.m_VertexStreams[i];
            vs.m_Source = 0x0;
            vs.m_Buffer = 0x0;
        }
    }

    void Clear(HContext context, uint32_t flags, uint8_t red, uint8_t green, uint8_t blue, uint8_t alpha, float depth, uint32_t stencil)
    {
        assert(context);
        uint32_t buffer_size = gdevice.m_DisplayWidth * gdevice.m_DisplayHeight;
        if (flags & dmGraphics::CLEAR_COLOUR_BUFFER)
        {
            uint32_t colour = (red << 24) | (green << 16) | (blue << 8) | alpha;
            uint32_t* buffer = (uint32_t*)gdevice.m_RenderBuffer->m_ColorBuffer;
            for (uint32_t i = 0; i < buffer_size; ++i)
                buffer[i] = colour;
        }
        if (flags & dmGraphics::CLEAR_DEPTH_BUFFER)
        {
            float* buffer = (float*)gdevice.m_RenderBuffer->m_DepthBuffer;
            for (uint32_t i = 0; i < buffer_size; ++i)
                buffer[i] = depth;
        }
        if (flags & dmGraphics::CLEAR_STENCIL_BUFFER)
        {
            uint32_t* buffer = (uint32_t*)gdevice.m_RenderBuffer->m_StencilBuffer;
            for (uint32_t i = 0; i < buffer_size; ++i)
                buffer[i] = stencil;
        }
    }

    void Flip()
    {
    }

    HVertexBuffer NewVertexBuffer(uint32_t size, const void* data, BufferUsage buffer_usage)
    {
        VertexBuffer* vb = new VertexBuffer();
        vb->m_Buffer = new char[size];
        vb->m_Copy = 0x0;
        vb->m_Size = size;
        if (size > 0 && data != 0x0)
            memcpy(vb->m_Buffer, data, size);
        return (uint32_t)vb;
    }

    void DeleteVertexBuffer(HVertexBuffer buffer)
    {
        VertexBuffer* vb = (VertexBuffer*)buffer;
        assert(vb->m_Copy == 0x0);
        delete [] vb->m_Buffer;
        delete vb;
    }

    void SetVertexBufferData(HVertexBuffer buffer, uint32_t size, const void* data, BufferUsage buffer_usage)
    {
        VertexBuffer* vb = (VertexBuffer*)buffer;
        memcpy(vb->m_Buffer, data, size);
    }

    void SetVertexBufferSubData(HVertexBuffer buffer, uint32_t offset, uint32_t size, const void* data)
    {
        VertexBuffer* vb = (VertexBuffer*)buffer;
        memcpy(&(vb->m_Buffer)[offset], data, size);
    }

    void* MapVertexBuffer(HVertexBuffer buffer, BufferAccess access)
    {
        VertexBuffer* vb = (VertexBuffer*)buffer;
        vb->m_Copy = new char[vb->m_Size];
        memcpy(vb->m_Copy, vb->m_Buffer, vb->m_Size);
        return vb->m_Copy;
    }

    bool UnmapVertexBuffer(HVertexBuffer buffer)
    {
        VertexBuffer* vb = (VertexBuffer*)buffer;
        memcpy(vb->m_Buffer, vb->m_Copy, vb->m_Size);
        delete [] vb->m_Copy;
        vb->m_Copy = 0x0;
        return true;
    }

    HIndexBuffer NewIndexBuffer(uint32_t size, const void* data, BufferUsage buffer_usage)
    {
        IndexBuffer* ib = new IndexBuffer();
        ib->m_Buffer = new char[size];
        ib->m_Copy = 0x0;
        ib->m_Size = size;
        memcpy(ib->m_Buffer, data, size);
        return (uint32_t)ib;
    }

    void DeleteIndexBuffer(HIndexBuffer buffer)
    {
        IndexBuffer* ib = (IndexBuffer*)buffer;
        assert(ib->m_Copy == 0x0);
        delete [] ib->m_Buffer;
        delete ib;
    }

    void SetIndexBufferData(HIndexBuffer buffer, uint32_t size, const void* data, BufferUsage buffer_usage)
    {
        IndexBuffer* ib = (IndexBuffer*)buffer;
        memcpy(ib->m_Buffer, data, size);
    }

    void SetIndexBufferSubData(HIndexBuffer buffer, uint32_t offset, uint32_t size, const void* data)
    {
        IndexBuffer* ib = (IndexBuffer*)buffer;
        memcpy(&(ib->m_Buffer)[offset], data, size);
    }

    void* MapIndexBuffer(HIndexBuffer buffer, BufferAccess access)
    {
        IndexBuffer* ib = (IndexBuffer*)buffer;
        ib->m_Copy = new char[ib->m_Size];
        memcpy(ib->m_Copy, ib->m_Buffer, ib->m_Size);
        return ib->m_Copy;
    }

    bool UnmapIndexBuffer(HIndexBuffer buffer)
    {
        IndexBuffer* ib = (IndexBuffer*)buffer;
        memcpy(ib->m_Buffer, ib->m_Copy, ib->m_Size);
        delete [] ib->m_Copy;
        ib->m_Copy = 0x0;
        return true;
    }

    HVertexDeclaration NewVertexDeclaration(VertexElement* element, uint32_t count)
    {
        VertexDeclaration* vd = new VertexDeclaration();
        memset(vd, 0, sizeof(*vd));
        for (uint32_t i = 0; i < count; ++i)
        {
            assert(vd->m_Elements[element[i].m_Stream].m_Size == 0);
            vd->m_Elements[element[i].m_Stream] = element[i];
        }
        return vd;
    }

    void DeleteVertexDeclaration(HVertexDeclaration vertex_declaration)
    {
        delete vertex_declaration;
    }

    void EnableVertexDeclaration(HContext context, HVertexDeclaration vertex_declaration, HVertexBuffer vertex_buffer)
    {
        assert(context);
        assert(vertex_declaration);
        assert(vertex_buffer);
        VertexBuffer* vb = (VertexBuffer*)vertex_buffer;
        uint16_t stride = 0;
        for (uint32_t i = 0; i < MAX_VERTEX_STREAM_COUNT; ++i)
            stride += vertex_declaration->m_Elements[i].m_Size * TYPE_SIZE[vertex_declaration->m_Elements[i].m_Type - dmGraphics::TYPE_BYTE];
        uint32_t offset = 0;
        for (uint16_t i = 0; i < MAX_VERTEX_STREAM_COUNT; ++i)
        {
            VertexElement& ve = vertex_declaration->m_Elements[i];
            if (ve.m_Size > 0)
            {
                SetVertexStream(context, i, ve.m_Size, ve.m_Type, stride, &vb->m_Buffer[offset]);
                offset += ve.m_Size * TYPE_SIZE[ve.m_Type - dmGraphics::TYPE_BYTE];
            }
        }
    }

    void DisableVertexDeclaration(HContext context, HVertexDeclaration vertex_declaration)
    {
        assert(context);
        assert(vertex_declaration);
        for (uint32_t i = 0; i < MAX_VERTEX_STREAM_COUNT; ++i)
            if (vertex_declaration->m_Elements[i].m_Size > 0)
                DisableVertexStream(context, i);
    }


    void SetVertexStream(HContext context, uint16_t stream, uint16_t size, Type type, uint16_t stride, const void* vertex_buffer)
    {
        assert(context);
        assert(vertex_buffer);
        VertexStream& s = gdevice.m_VertexStreams[stream];
        assert(s.m_Source == 0x0);
        assert(s.m_Buffer == 0x0);
        s.m_Source = vertex_buffer;
        s.m_Size = size * TYPE_SIZE[type - dmGraphics::TYPE_BYTE];
        s.m_Stride = stride;
    }

    void DisableVertexStream(HContext context, uint16_t stream)
    {
        assert(context);
        VertexStream& s = gdevice.m_VertexStreams[stream];
        s.m_Size = 0;
        if (s.m_Buffer != 0x0)
        {
            delete [] (char*)s.m_Buffer;
            s.m_Buffer = 0x0;
        }
        s.m_Source = 0x0;
    }

    static uint32_t GetIndex(Type type, const void* index_buffer, uint32_t index)
    {
        uint32_t result = ~0;
        switch (type)
        {
            case dmGraphics::TYPE_BYTE:
                result = ((char*)index_buffer)[index];
                break;
            case dmGraphics::TYPE_UNSIGNED_BYTE:
                result = ((unsigned char*)index_buffer)[index];
                break;
            case dmGraphics::TYPE_SHORT:
                result = ((short*)index_buffer)[index];
                break;
            case dmGraphics::TYPE_UNSIGNED_SHORT:
                result = ((unsigned short*)index_buffer)[index];
                break;
            case dmGraphics::TYPE_INT:
                result = ((int*)index_buffer)[index];
                break;
            case dmGraphics::TYPE_UNSIGNED_INT:
                result = ((unsigned int*)index_buffer)[index];
                break;
            case dmGraphics::TYPE_FLOAT:
                result = ((float*)index_buffer)[index];
                break;
        }
        return result;
    }

    void DrawElements(HContext context, PrimitiveType prim_type, uint32_t count, Type type, const void* index_buffer)
    {
        assert(context);
        assert(index_buffer);
        for (uint32_t i = 0; i < MAX_VERTEX_STREAM_COUNT; ++i)
        {
            VertexStream& vs = gdevice.m_VertexStreams[i];
            if (vs.m_Size > 0)
            {
                vs.m_Buffer = new char[vs.m_Size * count];
            }
        }
        for (uint32_t i = 0; i < count; ++i)
        {
            uint32_t index = GetIndex(type, index_buffer, i);
            for (uint32_t j = 0; j < MAX_VERTEX_STREAM_COUNT; ++j)
            {
                VertexStream& vs = gdevice.m_VertexStreams[j];
                if (vs.m_Size > 0)
                    memcpy(&((char*)vs.m_Buffer)[i * vs.m_Size], &((char*)vs.m_Source)[index * vs.m_Stride], vs.m_Size);
            }
        }
    }

    void DrawRangeElements(HContext context, PrimitiveType prim_type, uint32_t start, uint32_t count, Type type, HIndexBuffer index_buffer)
    {
        assert(context);
    }

    void Draw(HContext context, PrimitiveType prim_type, uint32_t first, uint32_t count)
    {
        assert(context);
    }

    HVertexProgram NewVertexProgram(const void* program, uint32_t program_size)
    {
        assert(program);
        char* p = new char[program_size];
        memcpy(p, program, program_size);
        return (uint32_t)p;
    }

    HFragmentProgram NewFragmentProgram(const void* program, uint32_t program_size)
    {
        assert(program);
        char* p = new char[program_size];
        memcpy(p, program, program_size);
        return (uint32_t)p;
    }

    void DeleteVertexProgram(HVertexProgram program)
    {
        assert(program);
        delete [] (char*)program;
    }

    void DeleteFragmentProgram(HFragmentProgram program)
    {
        assert(program);
        delete [] (char*)program;
    }

    void SetVertexProgram(HContext context, HVertexProgram program)
    {
        assert(context);
        gdevice.m_VertexProgram = (void*)program;
    }

    void SetFragmentProgram(HContext context, HFragmentProgram program)
    {
        assert(context);
        gdevice.m_FragmentProgram = (void*)program;
    }

    void SetViewport(HContext context, int width, int height)
    {
        assert(context);
        gdevice.m_DisplayWidth = width;
        gdevice.m_DisplayHeight = height;
        delete [] (char*)gdevice.m_FrameBuffer.m_ColorBuffer;
        delete [] (char*)gdevice.m_FrameBuffer.m_DepthBuffer;
        delete [] (char*)gdevice.m_FrameBuffer.m_AccumBuffer;
        delete [] (char*)gdevice.m_FrameBuffer.m_StencilBuffer;
        gdevice.m_FrameBuffer.m_ColorBuffer = new char[4 * gdevice.m_DisplayWidth * gdevice.m_DisplayHeight];
        gdevice.m_FrameBuffer.m_DepthBuffer = new char[4 * gdevice.m_DisplayWidth * gdevice.m_DisplayHeight];
        gdevice.m_FrameBuffer.m_AccumBuffer = new char[4 * gdevice.m_DisplayWidth * gdevice.m_DisplayHeight];
        gdevice.m_FrameBuffer.m_StencilBuffer = new char[4 * gdevice.m_DisplayWidth * gdevice.m_DisplayHeight];
    }

    void SetFragmentConstant(HContext context, const Vector4* data, int base_register)
    {
        assert(context);
        assert(gdevice.m_FragmentProgram != 0x0);
        memcpy(&gdevice.m_FragmentProgramRegisters[base_register], data, sizeof(Vector4));
    }

    void SetVertexConstantBlock(HContext context, const Vector4* data, int base_register, int num_vectors)
    {
        assert(context);
        assert(gdevice.m_VertexProgram != 0x0);
        memcpy(&gdevice.m_VertexProgramRegisters[base_register], data, sizeof(Vector4) * num_vectors);
    }

    void SetFragmentConstantBlock(HContext context, const Vector4* data, int base_register, int num_vectors)
    {
        assert(context);
        assert(gdevice.m_FragmentProgram != 0x0);
        memcpy(&gdevice.m_FragmentProgramRegisters[base_register], data, sizeof(Vector4) * num_vectors);
    }

    HRenderTarget NewRenderTarget(uint32_t width, uint32_t height, TextureFormat format)
    {
        RenderTarget* rt = new RenderTarget();

        rt->m_Texture = NewTexture();
        dmGraphics::SetTextureData(rt->m_Texture, 0, width, height, format, 0x0, 0);
        rt->m_RenderBuffer.m_ColorBuffer = new char[4 * width * height];
        rt->m_RenderBuffer.m_DepthBuffer = new char[4 * width * height];
        rt->m_RenderBuffer.m_AccumBuffer = new char[4 * width * height];
        rt->m_RenderBuffer.m_StencilBuffer = new char[4 * width * height];

        return rt;
    }

    void DeleteRenderTarget(HRenderTarget rt)
    {
        DeleteTexture(rt->m_Texture);
        delete [] (char*)rt->m_RenderBuffer.m_ColorBuffer;
        delete [] (char*)rt->m_RenderBuffer.m_DepthBuffer;
        delete [] (char*)rt->m_RenderBuffer.m_AccumBuffer;
        delete [] (char*)rt->m_RenderBuffer.m_StencilBuffer;
        delete rt;
    }

    void EnableRenderTarget(HContext context, HRenderTarget rendertarget)
    {
        assert(context);
        assert(rendertarget);
        gdevice.m_RenderBuffer = &rendertarget->m_RenderBuffer;
    }

    void DisableRenderTarget(HContext context, HRenderTarget rendertarget)
    {
        assert(context);
        assert(rendertarget);
        gdevice.m_RenderBuffer = &gdevice.m_FrameBuffer;
    }

    HTexture GetRenderTargetTexture(HRenderTarget rendertarget)
    {
        return rendertarget->m_Texture;
    }

    void SetTexture(HContext context, HTexture t)
    {
        assert(context);
        assert(t);
    }

    HTexture NewTexture()
    {
        Texture* tex = new Texture();
        tex->m_Data = 0x0;
        return tex;
    }

    void SetTextureData(HTexture texture,
                           uint16_t mip_map,
                           uint16_t width, uint16_t height,
                           TextureFormat texture_format, const void* data, uint32_t data_size)
    {
        assert(texture);
        if (texture->m_Data != 0x0)
            delete [] (char*)texture->m_Data;
        texture->m_Data = new char[TEXTURE_FORMAT_SIZE[texture_format] * width * height];
        texture->m_Format = texture_format;
        texture->m_Width = width;
        texture->m_Height = height;
        memcpy(texture->m_Data, data, data_size);
    }

    void DeleteTexture(HTexture t)
    {
        assert(t);
        if (t->m_Data != 0x0)
            delete [] (char*)t->m_Data;
        delete t;
    }

    void EnableState(HContext context, RenderState state)
    {
        assert(context);
    }

    void DisableState(HContext context, RenderState state)
    {
        assert(context);
    }

    void SetBlendFunc(HContext context, BlendFactor source_factor, BlendFactor destinaton_factor)
    {
        assert(context);
    }

    void SetColorMask(HContext context, bool red, bool green, bool blue, bool alpha)
    {
        assert(context);
        gdevice.m_RedMask = red;
        gdevice.m_GreenMask = green;
        gdevice.m_BlueMask = blue;
        gdevice.m_AlphaMask = alpha;
    }

    void SetDepthMask(HContext context, bool mask)
    {
        assert(context);
        gdevice.m_DepthMask = mask;
    }

    void SetIndexMask(HContext context, uint32_t mask)
    {
        assert(context);
        gdevice.m_IndexMask = mask;
    }

    void SetStencilMask(HContext context, uint32_t mask)
    {
        assert(context);
        gdevice.m_StencilMask = mask;
    }

    void SetCullFace(HContext context, FaceType face_type)
    {
        assert(context);
    }

    uint32_t GetWindowParam(WindowParam param)
    {
        switch (param)
        {
            case WINDOW_PARAM_OPENED:
                return gdevice.m_Opened;
            default:
                return 0;
        }
    }

    uint32_t GetWindowWidth()
    {
        return gdevice.m_DisplayWidth;
    }

    uint32_t GetWindowHeight()
    {
        return gdevice.m_DisplayHeight;
    }
}
